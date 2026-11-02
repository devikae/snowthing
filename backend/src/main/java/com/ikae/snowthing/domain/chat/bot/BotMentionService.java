package com.ikae.snowthing.domain.chat.bot;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.chat.dto.ChatErrorResponse;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.global.config.BotProperties;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.BotDelegationTokenService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.SharedRequestLimiter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BotMentionService {

    private static final String CHAT_DESTINATION = "/sub/chat/main";
    private static final int REQUESTS_PER_MINUTE = 1;

    private final BotProperties properties;
    private final BotMentionDetector detector;
    private final BotDelegationTokenService tokenService;
    private final BotGateway gateway;
    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Executor executor;
    private final SharedRequestLimiter sharedLimiter;
    private final Cache<Long, AtomicInteger> requestCounts =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterWrite(Duration.ofMinutes(1))
                    .build();

    public BotMentionService(
            BotProperties properties,
            BotMentionDetector detector,
            BotDelegationTokenService tokenService,
            BotGateway gateway,
            ChatService chatService,
            SimpMessagingTemplate messagingTemplate,
            @Qualifier("botTaskExecutor") Executor executor,
            SharedRequestLimiter sharedLimiter) {
        this.properties = properties;
        this.detector = detector;
        this.tokenService = tokenService;
        this.gateway = gateway;
        this.chatService = chatService;
        this.messagingTemplate = messagingTemplate;
        this.executor = executor;
        this.sharedLimiter = sharedLimiter;
    }

    public void submit(ChatMessageResponse userMessage, CustomUserDetails userDetails) {
        if (!properties.enabled() || userDetails == null || userDetails.getMember() == null) return;
        detector.questionFrom(userMessage.content())
                .ifPresent(question -> enqueue(userMessage, userDetails, question));
    }

    private void enqueue(
            ChatMessageResponse userMessage, CustomUserDetails userDetails, String question) {
        Long memberId = userDetails.getMember().getId();
        AtomicInteger count = requestCounts.get(memberId, ignored -> new AtomicInteger());
        if (count == null || count.incrementAndGet() > REQUESTS_PER_MINUTE) {
            log.warn("chat bot rate limit exceeded: messageId={}", userMessage.messageId());
            return;
        }
        try {
            executor.execute(() -> answer(userMessage, userDetails, question));
        } catch (RejectedExecutionException exception) {
            log.warn("chat bot queue full: messageId={}", userMessage.messageId());
            notifyRequester(userDetails, ErrorCode.CHAT_BOT_BUSY, 60);
        }
    }

    private void answer(
            ChatMessageResponse userMessage, CustomUserDetails userDetails, String question) {
        long startedAt = System.nanoTime();
        try {
            var decision =
                    sharedLimiter.acquire(
                            List.of(
                                    new SharedRequestLimiter.Limit(
                                            "snowthing:limits:{bot}:hour", 60, Duration.ofHours(1)),
                                    new SharedRequestLimiter.Limit(
                                            "snowthing:limits:{bot}:day",
                                            200,
                                            Duration.ofDays(1)),
                                    new SharedRequestLimiter.Limit(
                                            "snowthing:limits:{bot}:account:"
                                                    + userDetails.getPublicId()
                                                    + ":hour",
                                            5,
                                            Duration.ofHours(1))));
            if (!decision.allowed()) {
                notifyRequester(
                        userDetails,
                        decision.available()
                                ? ErrorCode.CHAT_BOT_BUDGET_EXCEEDED
                                : ErrorCode.CHAT_BOT_UNAVAILABLE,
                        decision.retryAfterSeconds());
                return;
            }
            String token = tokenService.issue(userDetails.getPublicId(), userMessage.messageId());
            BotAnswerResponse answer =
                    gateway.answer(
                            new BotAnswerRequest(
                                    userMessage.messageId(),
                                    question,
                                    userMessage.resortTag(),
                                    token));
            ChatMessageResponse botMessage =
                    chatService.createBotMessage(userMessage.resortTag(), answer.answer());
            messagingTemplate.convertAndSend(CHAT_DESTINATION, botMessage);
            log.info(
                    "chat bot completed: messageId={}, durationMs={}",
                    userMessage.messageId(),
                    (System.nanoTime() - startedAt) / 1_000_000L);
        } catch (CustomException exception) {
            log.warn(
                    "chat bot business error: messageId={}, errorCode={}, durationMs={}",
                    userMessage.messageId(),
                    exception.getErrorCode(),
                    (System.nanoTime() - startedAt) / 1_000_000L);
            notifyRequester(userDetails, exception.getErrorCode(), 0);
        } catch (Exception exception) {
            log.warn(
                    "chat bot failed: messageId={}, durationMs={}, type={}",
                    userMessage.messageId(),
                    (System.nanoTime() - startedAt) / 1_000_000L,
                    exception.getClass().getSimpleName());
        }
    }

    private void notifyRequester(CustomUserDetails user, ErrorCode error, long retryAfterSeconds) {
        ChatErrorResponse response =
                retryAfterSeconds > 0
                        ? ChatErrorResponse.retry(error, retryAfterSeconds)
                        : ChatErrorResponse.from(error);
        messagingTemplate.convertAndSendToUser(
                user.getUsername(),
                "/queue/errors",
                response);
    }
}
