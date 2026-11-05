package com.ikae.snowthing.domain.chat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.dto.SenderDto;
import com.ikae.snowthing.domain.chat.model.ResortTag;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.util.ContactFilter;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_CONTENT_LENGTH = 100;
    private static final int MAX_RATE_LIMIT_ENTRIES = 10_000;
    private static final long SEND_INTERVAL_MILLIS = 1_000L;
    private static final long MINUTE_WINDOW_MILLIS = 60_000L;
    private static final int MINUTE_MESSAGE_LIMIT = 20;
    private static final long DUPLICATE_WINDOW_MILLIS = 30_000L;
    private static final int DUPLICATE_HISTORY_SIZE = 5;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final Pattern BOT_MENTION_PREFIX = Pattern.compile("(?i)^@bot(?:\\s+|$)");
    private static final Pattern NORMALIZATION_IGNORED_PATTERN = Pattern.compile("[\\s\\p{Punct}]");

    private final ChatRecentHistoryBuffer chatRecentHistoryBuffer;
    private final Clock clock;

    private final Cache<Long, AbuseState> abuseStateCache =
            Caffeine.newBuilder()
                    .maximumSize(MAX_RATE_LIMIT_ENTRIES)
                    .expireAfterWrite(Duration.ofMillis(MINUTE_WINDOW_MILLIS))
                    .build();

    private record SentMessage(String normalizedContent, long sentAtMillis) {}

    /** 회원별 도배 판정 상태. 전송된 메시지만 기록하므로 거절된 문장은 다음 시도의 중복 판정에 들어가지 않는다. */
    private record AbuseState(
            long minuteStartedAtMillis, int minuteCount, List<SentMessage> recentMessages) {

        private static AbuseState empty(long now) {
            return new AbuseState(now, 0, List.of());
        }

        private ErrorCode rejectionFor(String normalizedContent, long now) {
            boolean duplicated =
                    recentMessages.stream()
                            .anyMatch(
                                    message ->
                                            message.normalizedContent().equals(normalizedContent)
                                                    && now - message.sentAtMillis()
                                                            < DUPLICATE_WINDOW_MILLIS);
            if (duplicated) {
                return ErrorCode.CHAT_DUPLICATE_MESSAGE;
            }
            if (!recentMessages.isEmpty()
                    && now - recentMessages.getLast().sentAtMillis() < SEND_INTERVAL_MILLIS) {
                return ErrorCode.CHAT_BURST_RATE_LIMIT;
            }
            if (now - minuteStartedAtMillis < MINUTE_WINDOW_MILLIS
                    && minuteCount >= MINUTE_MESSAGE_LIMIT) {
                return ErrorCode.CHAT_BURST_RATE_LIMIT;
            }
            return null;
        }

        private AbuseState withSent(String normalizedContent, long now) {
            List<SentMessage> updated = new ArrayList<>(recentMessages);
            updated.add(new SentMessage(normalizedContent, now));
            if (updated.size() > DUPLICATE_HISTORY_SIZE) {
                updated.removeFirst();
            }
            return now - minuteStartedAtMillis >= MINUTE_WINDOW_MILLIS
                    ? new AbuseState(now, 1, List.copyOf(updated))
                    : new AbuseState(minuteStartedAtMillis, minuteCount + 1, List.copyOf(updated));
        }
    }

    public ChatMessageResponse processMessage(
            ChatMessageRequest request, CustomUserDetails userDetails, String clientIp) {

        // 1. Authentication validation
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.CHAT_UNAUTHORIZED);
        }
        Member member = userDetails.getMember();

        // 2. Content validation
        String rawContent = request != null ? request.content() : null;
        if (rawContent == null || rawContent.trim().isEmpty()) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        String trimmedContent = rawContent.trim();
        if (trimmedContent.length() > MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }

        // 3. External link and messenger identifier blocking
        if (containsForbiddenContact(trimmedContent)) {
            throw new CustomException(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);
        }

        // 4. Per-member duplicate, send interval and per-minute checks
        String normalizedContent =
                NORMALIZATION_IGNORED_PATTERN
                        .matcher(trimmedContent)
                        .replaceAll("")
                        .toLowerCase(Locale.ROOT);
        long now = clock.millis();
        validateAbuseLimits(member.getId(), normalizedContent, now);

        // 5. Resort tag resolution
        ResortTag tag = ResortTag.from(request.resortTag());
        String resolvedTag = tag != null ? tag.name() : null;

        // 6. Construct response payload. React renders content as escaped JSX text.
        String messageId = UUID.randomUUID().toString();
        String sentAt = LocalDateTime.now().format(ISO_FORMATTER);
        SenderDto sender = new SenderDto(member.getPublicId(), member.getNickname());

        ChatMessageResponse response =
                new ChatMessageResponse(messageId, sender, resolvedTag, trimmedContent, sentAt);
        chatRecentHistoryBuffer.append(response);
        return response;
    }

    public List<ChatMessageResponse> getRecentMessages() {
        return chatRecentHistoryBuffer.getRecentMessages();
    }

    public ChatMessageResponse createBotMessage(String resortTag, String content) {
        String safeContent = content == null ? "" : content.trim();
        if (containsForbiddenContact(safeContent) || ContactFilter.containsPhone(safeContent)) {
            throw new CustomException(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);
        }
        if (safeContent.length() > MAX_CONTENT_LENGTH) {
            int end =
                    safeContent.offsetByCodePoints(
                            0,
                            Math.min(
                                    MAX_CONTENT_LENGTH,
                                    safeContent.codePointCount(0, safeContent.length())));
            safeContent = safeContent.substring(0, end);
        }
        if (safeContent.isEmpty()
                || containsForbiddenContact(safeContent)
                || ContactFilter.containsPhone(safeContent)) {
            throw new CustomException(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);
        }
        ChatMessageResponse response =
                new ChatMessageResponse(
                        UUID.randomUUID().toString(),
                        new SenderDto("snowthing-ai", "스노씽 AI"),
                        resortTag,
                        safeContent,
                        LocalDateTime.now().format(ISO_FORMATTER));
        chatRecentHistoryBuffer.append(response);
        return response;
    }

    /** Cache eviction for tests or periodic maintenance */
    public void clearCache() {
        abuseStateCache.invalidateAll();
        chatRecentHistoryBuffer.clear();
    }

    private boolean containsForbiddenContact(String content) {
        String contactCheckTarget = BOT_MENTION_PREFIX.matcher(content).replaceFirst("");
        return ContactFilter.containsLinkOrMessenger(contactCheckTarget);
    }

    private void validateAbuseLimits(Long memberId, String normalizedContent, long now) {
        AtomicReference<ErrorCode> rejection = new AtomicReference<>();
        abuseStateCache
                .asMap()
                .compute(
                        memberId,
                        (ignored, current) -> {
                            AbuseState state = current == null ? AbuseState.empty(now) : current;
                            rejection.set(state.rejectionFor(normalizedContent, now));
                            return rejection.get() == null
                                    ? state.withSent(normalizedContent, now)
                                    : state;
                        });
        if (rejection.get() != null) {
            throw new CustomException(rejection.get());
        }
    }
}
