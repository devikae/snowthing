package com.ikae.snowthing.domain.chat.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

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
    private static final long DUPLICATE_COOLDOWN_MILLIS = 5000L;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // External URL and messenger blocking patterns
    private static final Pattern EXTERNAL_LINK_PATTERN =
            Pattern.compile(
                    "(?i).*(https?://|www\\.|\\b[a-zA-Z0-9.-]+\\.(com|net|org|kr|io|xyz|top|me|cc|tv)\\b|t\\.me/|telegram|open\\.kakao\\.com|@[a-zA-Z0-9_]{3,}).*");

    private final ChatAuditLogger chatAuditLogger;
    private final ChatRecentHistoryBuffer chatRecentHistoryBuffer;

    // In-memory duplicate message cache per member_id: (memberId -> LastMessageInfo)
    private final Map<Long, LastMessageInfo> recentMessageCache = new ConcurrentHashMap<>();

    private record LastMessageInfo(String normalizedContent, long sentAtMillis) {}

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

        // 3. External link and messenger regex blocking
        if (EXTERNAL_LINK_PATTERN.matcher(trimmedContent).matches()) {
            throw new CustomException(ErrorCode.CHAT_EXTERNAL_LINK_FORBIDDEN);
        }

        // 4. Duplicate message check (normalized text + 5-sec cooldown)
        String normalizedContent = trimmedContent.replaceAll("[\\s\\p{Punct}]", "").toLowerCase();
        long now = System.currentTimeMillis();
        LastMessageInfo lastInfo = recentMessageCache.get(member.getId());

        if (lastInfo != null
                && lastInfo.normalizedContent().equals(normalizedContent)
                && (now - lastInfo.sentAtMillis()) < DUPLICATE_COOLDOWN_MILLIS) {
            throw new CustomException(ErrorCode.CHAT_DUPLICATE_MESSAGE);
        }
        recentMessageCache.put(member.getId(), new LastMessageInfo(normalizedContent, now));

        // 5. XSS Sanitization (HTML Entity Escaping)
        String sanitizedContent = HtmlUtils.htmlEscape(trimmedContent);

        // 6. Resort tag resolution
        ResortTag tag = ResortTag.from(request.resortTag());
        String resolvedTag = tag != null ? tag.name() : null;

        // 7. Audit log (Communications Secrets Act 3-month retention)
        chatAuditLogger.log(member.getId(), clientIp, "MAIN_CHAT");

        // 8. Construct response payload
        String messageId = UUID.randomUUID().toString();
        String sentAt = LocalDateTime.now().format(ISO_FORMATTER);
        SenderDto sender = new SenderDto(member.getPublicId(), member.getNickname());

        ChatMessageResponse response =
                new ChatMessageResponse(messageId, sender, resolvedTag, sanitizedContent, sentAt);
        chatRecentHistoryBuffer.append(response);
        return response;
    }

    public List<ChatMessageResponse> getRecentMessages() {
        return chatRecentHistoryBuffer.getRecentMessages();
    }

    /** Cache eviction for tests or periodic maintenance */
    public void clearCache() {
        recentMessageCache.clear();
        chatRecentHistoryBuffer.clear();
    }
}
