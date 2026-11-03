package com.ikae.snowthing.domain.chat.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.admin.service.ChatIpBanService;
import com.ikae.snowthing.domain.admin.service.SystemConfigService;
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
    private static final Pattern BOT_MENTION_PREFIX = Pattern.compile("(?i)^@bot(?:\\s+|$)");
    private static final Pattern BOT_PHONE_PATTERN =
            Pattern.compile(
                    "(?<![0-9])(?:\\+82[ .-]?(?:1[016789]|2|[3-6][1-5]|70)|0(?:1[016789]|2|[3-6][1-5]|70))[ .-]?[0-9]{3,4}[ .-]?[0-9]{4}(?![0-9])");
    private static final Pattern NORMALIZATION_IGNORED_PATTERN = Pattern.compile("[\\s\\p{Punct}]");

    private static final String[] ANONYMOUS_PREFIXES = {
        "눈사람", "카빙꿈나무", "스노우러버", "파우더헌터", "트릭마스터",
        "슬로프요정", "안전라이더", "시즌권러버", "설질지킴이", "덕스탠스"
    };

    private final ChatRecentHistoryBuffer chatRecentHistoryBuffer;
    private final ChatIpBanService chatIpBanService;
    private final SystemConfigService systemConfigService;

    private final Cache<String, LastMessageInfo> anonRecentMessageCache =
            Caffeine.newBuilder()
                    .maximumSize(MAX_RATE_LIMIT_ENTRIES)
                    .expireAfterWrite(Duration.ofMillis(DUPLICATE_COOLDOWN_MILLIS))
                    .build();
    private final Cache<String, BurstWindow> anonBurstWindowCache =
            Caffeine.newBuilder()
                    .maximumSize(MAX_RATE_LIMIT_ENTRIES)
                    .expireAfterWrite(Duration.ofMillis(BURST_WINDOW_MILLIS))
                    .build();

    private final Cache<Long, LastMessageInfo> recentMessageCache =
            Caffeine.newBuilder()
                    .maximumSize(MAX_RATE_LIMIT_ENTRIES)
                    .expireAfterWrite(Duration.ofMillis(DUPLICATE_COOLDOWN_MILLIS))
                    .build();
    private final Cache<Long, BurstWindow> burstWindowCache =
            Caffeine.newBuilder()
                    .maximumSize(MAX_RATE_LIMIT_ENTRIES)
                    .expireAfterWrite(Duration.ofMillis(BURST_WINDOW_MILLIS))
                    .build();

    private record LastMessageInfo(String normalizedContent, long sentAtMillis) {}

    private record BurstWindow(long startedAtMillis, int attempts) {}

    public ChatMessageResponse processMessage(
            ChatMessageRequest request, CustomUserDetails userDetails, String clientIp) {

        // 0. IP Ban Check
        if (chatIpBanService.isBanned(clientIp)) {
            throw new CustomException(ErrorCode.CHAT_IP_BANNED);
        }

        // 1. Content validation
        String rawContent = request != null ? request.content() : null;
        if (rawContent == null || rawContent.trim().isEmpty()) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_EMPTY);
        }
        String trimmedContent = rawContent.trim();
        if (trimmedContent.length() > MAX_CONTENT_LENGTH) {
            throw new CustomException(ErrorCode.CHAT_MESSAGE_TOO_LONG);
        }

        // 2. External link and messenger identifier blocking
        if (containsForbiddenContact(trimmedContent)) {
            throw new CustomException(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);
        }

        // 3. Normalization for duplicate and abuse check
        String normalizedContent =
                NORMALIZATION_IGNORED_PATTERN
                        .matcher(trimmedContent)
                        .replaceAll("")
                        .toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        // 4. Authentication validation and sender / abuse check resolution
        SenderDto sender;
        if (userDetails != null && userDetails.getMember() != null) {
            Member member = userDetails.getMember();
            sender = new SenderDto(member.getPublicId(), member.getNickname());
            validateAbuseLimits(member.getId(), normalizedContent, now);
        } else {
            if (!systemConfigService.isAnonymousChatEnabled()) {
                throw new CustomException(ErrorCode.CHAT_UNAUTHORIZED);
            }
            String anonNickname = generateAnonymousNickname(clientIp);
            String anonId = "anon-" + UUID.randomUUID();
            sender = new SenderDto(anonId, anonNickname);
            validateAnonymousAbuseLimits(clientIp, normalizedContent, now);
        }

        // 5. Resort tag resolution
        ResortTag tag = ResortTag.from(request.resortTag());
        String resolvedTag = tag != null ? tag.name() : null;

        // 6. Construct response payload. React renders content as escaped JSX text.
        String messageId = UUID.randomUUID().toString();
        String sentAt = LocalDateTime.now().format(ISO_FORMATTER);

        ChatMessageResponse response =
                new ChatMessageResponse(messageId, sender, resolvedTag, trimmedContent, sentAt);
        chatRecentHistoryBuffer.append(response, clientIp);
        return response;
    }

    private String generateAnonymousNickname(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "눈사람#" + String.format("%04d", (int) (Math.random() * 10000));
        }
        int hash = Math.abs(clientIp.hashCode());
        String prefix = ANONYMOUS_PREFIXES[hash % ANONYMOUS_PREFIXES.length];
        int num = hash % 10000;
        return prefix + "#" + String.format("%04d", num);
    }

    private void validateAnonymousAbuseLimits(String clientIp, String normalizedContent, long now) {
        String key = clientIp != null ? clientIp : "unknown";
        BurstWindow window = anonBurstWindowCache.get(key, k -> new BurstWindow(now, 0));
        if (window != null) {
            if (now - window.startedAtMillis() > BURST_WINDOW_MILLIS) {
                anonBurstWindowCache.put(key, new BurstWindow(now, 1));
            } else {
                int attempts = window.attempts() + 1;
                if (attempts > BURST_ATTEMPT_LIMIT) {
                    throw new CustomException(ErrorCode.CHAT_BURST_RATE_LIMIT);
                }
                anonBurstWindowCache.put(key, new BurstWindow(window.startedAtMillis(), attempts));
            }
        }
        LastMessageInfo lastMessage = anonRecentMessageCache.getIfPresent(key);
        if (lastMessage != null
                && now - lastMessage.sentAtMillis() <= DUPLICATE_COOLDOWN_MILLIS
                && lastMessage.normalizedContent().equals(normalizedContent)) {
            throw new CustomException(ErrorCode.CHAT_DUPLICATE_MESSAGE);
        }
        anonRecentMessageCache.put(key, new LastMessageInfo(normalizedContent, now));
    }

    public List<ChatMessageResponse> getRecentMessages() {
        return chatRecentHistoryBuffer.getRecentMessages();
    }

    public ChatMessageResponse createBotMessage(String resortTag, String content) {
        String safeContent = content == null ? "" : content.trim();
        if (containsForbiddenContact(safeContent)
                || BOT_PHONE_PATTERN.matcher(safeContent).find()) {
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
                || BOT_PHONE_PATTERN.matcher(safeContent).find()) {
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
        recentMessageCache.invalidateAll();
        burstWindowCache.invalidateAll();
        chatRecentHistoryBuffer.clear();
    }

    private boolean containsForbiddenContact(String content) {
        String contactCheckTarget = BOT_MENTION_PREFIX.matcher(content).replaceFirst("");
        return URL_PATTERN.matcher(contactCheckTarget).find()
                || MESSENGER_IDENTIFIER_PATTERN.matcher(contactCheckTarget).find();
    }

    private void validateAbuseLimits(Long memberId, String normalizedContent, long now) {
        validateBurstLimit(memberId, now);
        validateDuplicateMessage(memberId, normalizedContent, now);
    }

    private void validateBurstLimit(Long memberId, long now) {
        AtomicBoolean rejected = new AtomicBoolean();
        burstWindowCache
                .asMap()
                .compute(
                        memberId,
                        (ignored, current) -> {
                            BurstWindow updated =
                                    current == null
                                                    || now - current.startedAtMillis()
                                                            >= BURST_WINDOW_MILLIS
                                            ? new BurstWindow(now, 1)
                                            : new BurstWindow(
                                                    current.startedAtMillis(),
                                                    current.attempts() + 1);
                            rejected.set(updated.attempts() >= BURST_ATTEMPT_LIMIT);
                            return updated;
                        });
        if (rejected.get()) {
            throw new CustomException(ErrorCode.CHAT_BURST_RATE_LIMIT);
        }
    }

    private void validateDuplicateMessage(Long memberId, String normalizedContent, long now) {
        AtomicBoolean rejected = new AtomicBoolean();
        recentMessageCache
                .asMap()
                .compute(
                        memberId,
                        (ignored, current) -> {
                            if (current != null
                                    && current.normalizedContent().equals(normalizedContent)
                                    && now - current.sentAtMillis() < DUPLICATE_COOLDOWN_MILLIS) {
                                rejected.set(true);
                                return current;
                            }
                            return new LastMessageInfo(normalizedContent, now);
                        });
        if (rejected.get()) {
            throw new CustomException(ErrorCode.CHAT_DUPLICATE_MESSAGE);
        }
    }
}
