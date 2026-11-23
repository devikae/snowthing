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
import com.ikae.snowthing.domain.admin.service.ChatIpBanService;
import com.ikae.snowthing.domain.admin.service.SystemConfigService;
import com.ikae.snowthing.domain.chat.dto.ChatMessageRequest;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.dto.SenderDto;
import com.ikae.snowthing.domain.chat.model.ResortTag;
import com.ikae.snowthing.domain.contentfilter.service.ContentFilterService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.util.ContactFilter;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
    private final ChatIpBanService chatIpBanService;
    private final SystemConfigService systemConfigService;
    private final ContentFilterService contentFilterService;

    /** 도배 판정 상태. 회원은 "member:{id}", 비회원은 "ip:{ip}" 키로 같은 규칙을 쓴다. */
    private final Cache<String, AbuseState> abuseStateCache =
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

    /** 전송 결과 (IKA-115). held가 true면 금칙어에 걸린 메시지다. 보낸 사람에게만 돌려주고 방송하지 않으며 최근 대화에도 넣지 않는다. */
    public record SendResult(ChatMessageResponse message, boolean held) {}

    /** 검사를 통과한 메시지를 돌려준다. 금칙어에 걸린 메시지도 돌려주므로 방송하는 쪽은 {@link #send}로 보류 여부를 봐야 한다. */
    public ChatMessageResponse processMessage(
            ChatMessageRequest request, CustomUserDetails userDetails, String clientIp) {
        return send(request, userDetails, clientIp).message();
    }

    public SendResult send(
            ChatMessageRequest request, CustomUserDetails userDetails, String clientIp) {

        // 1. IP ban and authentication validation. 비회원은 관리자가 켠 경우에만 보낼 수 있다.
        if (chatIpBanService.isBanned(clientIp)) {
            throw new CustomException(ErrorCode.CHAT_IP_BANNED);
        }
        Member member = userDetails != null ? userDetails.getMember() : null;
        // 관리자가 라이브톡을 닫으면 관리자 말고는 보낼 수 없다 (IKA-115). 관리자는 닫힌 동안 안내를 남길 수 있다.
        boolean admin = member != null && member.getRole() == Role.ROLE_ADMIN;
        if (!admin && !systemConfigService.isChatEnabled()) {
            throw new CustomException(ErrorCode.CHAT_KILL_SWITCH_ACTIVE);
        }
        if (member == null && !systemConfigService.isAnonymousChatEnabled()) {
            throw new CustomException(ErrorCode.CHAT_UNAUTHORIZED);
        }

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
        // 비회원은 다른 제한기와 같은 정규화 키를 쓴다. IPv6 주소 끝자리를 바꿔도 같은 도배 기록에 걸린다.
        String abuseKey =
                member != null
                        ? "member:" + member.getId()
                        : "ip:" + ClientIpResolver.normalizeForLimit(clientIp);
        validateAbuseLimits(abuseKey, normalizedContent, now);

        // 5. Resort tag resolution
        ResortTag tag = ResortTag.from(request.resortTag());
        String resolvedTag = tag != null ? tag.name() : null;

        // 6. Construct response payload. React renders content as escaped JSX text.
        String messageId = UUID.randomUUID().toString();
        String sentAt = LocalDateTime.now().format(ISO_FORMATTER);
        SenderDto sender =
                member != null
                        ? new SenderDto(
                                member.getPublicId(),
                                member.getNickname(),
                                member.getProfileImageUrl())
                        : new SenderDto("anon-" + UUID.randomUUID(), anonymousNickname(clientIp));

        ChatMessageResponse response =
                new ChatMessageResponse(messageId, sender, resolvedTag, trimmedContent, sentAt);

        // 7. 금칙어는 글, 댓글처럼 조용히 보류한다. 보낸 사람 화면에는 평소처럼 보이고 다른 사람에게는 가지 않는다.
        // 걸린 것을 알아채고 표현을 바꿔 다시 보내지 못하게 하려는 선택이다. 라이브톡은 본문을 저장하지 않으므로 보류 기록도 남기지 않는다.
        if (contentFilterService.inspect(userDetails, trimmedContent).isPresent()) {
            log.info("Live chat message held by banned word: member={}", member != null);
            return new SendResult(response, true);
        }
        chatRecentHistoryBuffer.append(response, clientIp);
        return new SendResult(response, false);
    }

    /** 비회원이 받을 닉네임 (IKA-115). 같은 IP는 늘 같은 이름이므로 보내기 전에 미리 알려 줄 수 있다. */
    public String guestNickname(String clientIp) {
        return anonymousNickname(clientIp);
    }

    /** 같은 IP는 같은 닉네임을 받는다. IP 원문은 내보내지 않는다. */
    private String anonymousNickname(String clientIp) {
        return AnonymousNickname.of(clientIp);
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

    private void validateAbuseLimits(String abuseKey, String normalizedContent, long now) {
        AtomicReference<ErrorCode> rejection = new AtomicReference<>();
        abuseStateCache
                .asMap()
                .compute(
                        abuseKey,
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
