package com.ikae.snowthing.domain.chat.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.chat.audit.ChatAuditLogger;
import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.dto.SenderDto;
import com.ikae.snowthing.domain.chat.model.ResortTag;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MAX_CONTENT_LENGTH = 100;
    private static final int BURST_ATTEMPT_LIMIT = 5;
    private static final int MAX_RATE_LIMIT_ENTRIES = 10_000;
    private static final long BURST_WINDOW_MILLIS = 1_000L;
    private static final long DUPLICATE_COOLDOWN_MILLIS = 5000L;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String AUDIT_CHANNEL = "MAIN_CHAT";

    private static final Pattern URL_PATTERN =
            Pattern.compile(
                    "(?i)(?:https?://|www\\.)\\S+"
                            + "|\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+"
                            + "(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})(?::\\d{1,5})?(?:/\\S*)?"
                            + "|\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}"
                            + "(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?::\\d{1,5})?(?:/\\S*)?");
    private static final Pattern MESSENGER_IDENTIFIER_PATTERN =
            Pattern.compile(
                    "(?i)(?:\\bt\\.me/\\S+|\\btelegram\\b|\\bopen\\.kakao\\.com/\\S+|(?<![\\w.])@[a-z0-9_]{3,})");
    private static final Pattern NORMALIZATION_IGNORED_PATTERN = Pattern.compile("[\\s\\p{Punct}]");

    private final ChatAuditLogger chatAuditLogger;
    private final ChatRecentHistoryBuffer chatRecentHistoryBuffer;

    private final Object abuseGuardMonitor = new Object();
    private final Map<Long, LastMessageInfo> recentMessageCache =
            new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Long, BurstWindow> burstWindowCache = new LinkedHashMap<>(16, 0.75f, true);

    private record LastMessageInfo(String normalizedContent, long sentAtMillis) {}

    private record BurstWindow(long startedAtMillis, int attempts) {}

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

        // 4. Per-member burst and duplicate checks
        String normalizedContent =
                NORMALIZATION_IGNORED_PATTERN
                        .matcher(trimmedContent)
                        .replaceAll("")
                        .toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        validateAbuseLimits(member.getId(), normalizedContent, now);

        // 5. Resort tag resolution
        ResortTag tag = ResortTag.from(request.resortTag());
        String resolvedTag = tag != null ? tag.name() : null;

        // 6. Audit log (Communications Secrets Act 3-month retention)
        chatAuditLogger.log(member.getId(), clientIp, AUDIT_CHANNEL);

        // 7. Construct response payload. React renders content as escaped JSX text.
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

    /** Cache eviction for tests or periodic maintenance */
    public void clearCache() {
        synchronized (abuseGuardMonitor) {
            recentMessageCache.clear();
            burstWindowCache.clear();
        }
        chatRecentHistoryBuffer.clear();
    }

    private boolean containsForbiddenContact(String content) {
        return URL_PATTERN.matcher(content).find()
                || MESSENGER_IDENTIFIER_PATTERN.matcher(content).find();
    }

    private void validateAbuseLimits(Long memberId, String normalizedContent, long now) {
        synchronized (abuseGuardMonitor) {
            evictExpiredEntries(now);
            try {
                validateBurstLimit(memberId, now);
                validateDuplicateMessage(memberId, normalizedContent, now);
            } finally {
                trimToMaximumSize(burstWindowCache);
                trimToMaximumSize(recentMessageCache);
            }
        }
    }

    private void validateBurstLimit(Long memberId, long now) {
        BurstWindow current = burstWindowCache.get(memberId);
        BurstWindow updated;
        if (current == null || now - current.startedAtMillis() >= BURST_WINDOW_MILLIS) {
            updated = new BurstWindow(now, 1);
        } else {
            updated = new BurstWindow(current.startedAtMillis(), current.attempts() + 1);
        }
        burstWindowCache.put(memberId, updated);
        if (updated.attempts() >= BURST_ATTEMPT_LIMIT) {
            throw new CustomException(ErrorCode.CHAT_BURST_RATE_LIMIT);
        }
    }

    private void validateDuplicateMessage(Long memberId, String normalizedContent, long now) {
        LastMessageInfo lastInfo = recentMessageCache.get(memberId);
        if (lastInfo != null
                && lastInfo.normalizedContent().equals(normalizedContent)
                && now - lastInfo.sentAtMillis() < DUPLICATE_COOLDOWN_MILLIS) {
            throw new CustomException(ErrorCode.CHAT_DUPLICATE_MESSAGE);
        }
        recentMessageCache.put(memberId, new LastMessageInfo(normalizedContent, now));
    }

    private void evictExpiredEntries(long now) {
        burstWindowCache
                .entrySet()
                .removeIf(entry -> now - entry.getValue().startedAtMillis() >= BURST_WINDOW_MILLIS);
        recentMessageCache
                .entrySet()
                .removeIf(
                        entry ->
                                now - entry.getValue().sentAtMillis() >= DUPLICATE_COOLDOWN_MILLIS);
    }

    private <T> void trimToMaximumSize(Map<Long, T> cache) {
        Iterator<Long> keys = cache.keySet().iterator();
        while (cache.size() > MAX_RATE_LIMIT_ENTRIES && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }
}
