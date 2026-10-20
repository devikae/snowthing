package com.ikae.snowthing.domain.chat.bot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.config.BotProperties;
import com.ikae.snowthing.global.security.BotDelegationTokenService;
import com.ikae.snowthing.global.security.CustomUserDetails;

class BotMentionServiceTest {

    private BotMentionDetector detector;
    private BotDelegationTokenService tokenService;
    private BotGateway gateway;
    private ChatService chatService;
    private BotMentionService service;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        detector = mock(BotMentionDetector.class);
        tokenService = mock(BotDelegationTokenService.class);
        gateway = mock(BotGateway.class);
        chatService = mock(ChatService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        BotProperties properties =
                new BotProperties(
                        true,
                        URI.create("http://127.0.0.1:3100"),
                        "0123456789abcdef0123456789abcdef",
                        "abcdef0123456789abcdef0123456789",
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(15));
        service =
                new BotMentionService(
                        properties,
                        detector,
                        tokenService,
                        gateway,
                        chatService,
                        messagingTemplate,
                        Runnable::run);

        Member member =
                Member.builder()
                        .publicId("0191a1b2-c3d4-e5f6-a7b8-c9d0e1f2a3b4")
                        .email("bot-test@snowthing.org")
                        .nickname("봇테스터")
                        .build();
        ReflectionTestUtils.setField(member, "id", 1L);
        userDetails = new CustomUserDetails(member);

        when(detector.questionFrom(any())).thenReturn(Optional.of("최근 매물 알려줘"));
        when(tokenService.issue(any(), any())).thenReturn("delegation-token");
        when(gateway.answer(any())).thenReturn(new BotAnswerResponse("request-1", "최근 매물입니다."));
        when(chatService.createBotMessage(any(), any()))
                .thenReturn(
                        new ChatMessageResponse(
                                "bot-message",
                                null,
                                "GENERAL",
                                "최근 매물입니다.",
                                "2026-10-20T16:00:00"));
    }

    @Test
    @DisplayName("같은 사용자의 봇 호출은 분당 한 번만 처리한다")
    void submit_RateLimitsToOneRequestPerMinute() {
        ChatMessageResponse first =
                new ChatMessageResponse(
                        "message-1", null, "GENERAL", "@bot 최근 매물 알려줘", "2026-10-20T16:00:00");
        ChatMessageResponse second =
                new ChatMessageResponse(
                        "message-2", null, "GENERAL", "@bot 최근 게시글 알려줘", "2026-10-20T16:00:01");

        service.submit(first, userDetails);
        service.submit(second, userDetails);

        verify(gateway, times(1)).answer(any());
    }
}
