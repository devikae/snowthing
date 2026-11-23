package com.ikae.snowthing.domain.admin.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.ikae.snowthing.domain.admin.dto.ChatBannedIpResponse;
import com.ikae.snowthing.domain.admin.entity.ChatBannedIp;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.admin.repository.ChatBannedIpRepository;
import com.ikae.snowthing.domain.chat.service.ChatRecentHistoryBuffer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.web.IpRange;

import lombok.extern.slf4j.Slf4j;

/**
 * IP 차단 목록 하나로 라이브톡, 게시글 작성, 댓글 작성을 막는다 (IKA-96). 이름은 라이브톡 전용이던 때 그대로 둔다.
 *
 * <ul>
 *   <li>저장은 기존 chat_banned_ip 표를 그대로 쓴다. 예전 단일 IP 차단은 IPv4는 /32, IPv6는 같은 /64 대역으로 읽혀 계속 동작한다.
 *   <li>모든 쓰기 요청이 확인하므로 활성 차단을 메모리에 들고 대역마다 비교한다. 차단은 수십 건 수준이라 선형 비교로 충분하다.
 *   <li>차단 줄마다 따로 비교하므로 같은 IP나 겹치는 대역에 차단이 여러 줄이면 가장 늦게 끝나는 차단까지 막힌다. 한 줄을 풀어도 남은 줄은 그대로 동작한다.
 *   <li>관리자가 바꾸면 커밋 뒤 DB에서 다시 읽는다. 롤백된 변경이 메모리에 남지 않는다. 만료는 비교할 때 시각으로 거른다.
 * </ul>
 */
@Slf4j
@Service
public class ChatIpBanService {

    /** 너무 넓은 대역을 실수로 막지 않게 하는 최소 길이. IPv4 /16은 6만 5천 개, IPv6 /32는 통신사 하나 규모다. */
    static final int MIN_IPV4_PREFIX = 16;

    static final int MIN_IPV6_PREFIX = 32;

    /** 보류 글에서 바로 막을 때 쓰는 대역. IPv6은 가정 하나가 받는 /64, IPv4는 공유 IP가 많아 주소 하나만 막는다. */
    static final int HOLD_SHORTCUT_IPV6_PREFIX = 64;

    /** 메시지 ID는 UUID(36자)다. 조치 기록 target_id 칸(64자)을 넘는 값은 받지 않는다. */
    static final int MAX_MESSAGE_ID_LENGTH = 64;

    private final ChatBannedIpRepository repository;
    private final ChatRecentHistoryBuffer historyBuffer;
    private final SimpMessagingTemplate messagingTemplate;
    private final AdminActionLogger actionLogger;

    private volatile List<ActiveBan> activeBans = List.of();

    public ChatIpBanService(
            ChatBannedIpRepository repository,
            ChatRecentHistoryBuffer historyBuffer,
            @Lazy SimpMessagingTemplate messagingTemplate,
            AdminActionLogger actionLogger) {
        this.repository = repository;
        this.historyBuffer = historyBuffer;
        this.messagingTemplate = messagingTemplate;
        this.actionLogger = actionLogger;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /** DB의 활성 차단을 다시 읽어 메모리 목록을 바꾼다. 형식이 틀린 옛 값은 건너뛴다. */
    public void reload() {
        List<ActiveBan> loaded = new ArrayList<>();
        for (ChatBannedIp ban : repository.findAllActiveBans(LocalDateTime.now())) {
            IpRange.parse(ban.getBannedIp())
                    .map(ChatIpBanService::effectiveRange)
                    .ifPresentOrElse(
                            range -> loaded.add(new ActiveBan(range, ban.getBannedUntil())),
                            () -> log.warn("Skipping unparsable IP ban: id={}", ban.getId()));
        }
        activeBans = List.copyOf(loaded);
        log.info("Loaded {} active IP bans into memory", loaded.size());
    }

    public boolean isBanned(String ip) {
        if (ip == null || ip.isBlank()) return false;
        List<ActiveBan> bans = activeBans;
        if (bans.isEmpty()) return false;
        LocalDateTime now = LocalDateTime.now();
        for (ActiveBan ban : bans) {
            if (ban.isActive(now) && ban.range().contains(ip)) {
                return true;
            }
        }
        return false;
    }

    /** 이 IP를 막는 활성 차단 중 가장 늦게 끝나는 시각. 영구 차단은 MAX, 막히지 않았으면 null이다. */
    public LocalDateTime bannedUntil(String ip) {
        if (ip == null || ip.isBlank()) return null;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime latest = null;
        for (ActiveBan ban : activeBans) {
            if (!ban.isActive(now) || !ban.range().contains(ip)) continue;
            LocalDateTime until = ban.until() == null ? LocalDateTime.MAX : ban.until();
            if (latest == null || until.isAfter(latest)) latest = until;
        }
        return latest;
    }

    /** 게시글, 댓글 작성 전에 부른다. 차단 내용은 알려 주지 않는 일반 오류로 막는다. */
    public void requireWritable(String ip) {
        if (isBanned(ip)) {
            throw new CustomException(ErrorCode.WRITE_NOT_ALLOWED);
        }
    }

    @Transactional(readOnly = true)
    public List<ChatBannedIpResponse> getBannedIps() {
        return List.copyOf(
                repository.findAllByOrderByCreatedAtDescIdDesc().stream()
                        .map(ChatBannedIpResponse::from)
                        .toList());
    }

    @Transactional
    public ChatBannedIpResponse banIp(
            String ipOrRange, String reason, LocalDateTime until, String createdBy) {
        IpRange range = validRange(ipOrRange);
        ChatBannedIp saved =
                repository.save(
                        ChatBannedIp.builder()
                                .bannedIp(range.toCanonicalString())
                                .reason(reason)
                                .bannedUntil(until)
                                .createdBy(createdBy)
                                .build());
        actionLogger.record(
                AdminActionType.CHAT_IP_BAN,
                AdminTargetType.CHAT_BAN,
                saved.getId(),
                range.toCanonicalString() + " " + reason);
        IpRange effective = effectiveRange(range);
        // 메모리 반영과 메시지 삭제는 커밋 뒤에 한다. 롤백된 차단이 메모리에 남아 사용자를 막지 않게 하려는 것이다.
        afterCommit(
                () -> {
                    reload();
                    List<String> purgedMessageIds =
                            historyBuffer.removeByIpMatching(effective::contains);
                    if (!purgedMessageIds.isEmpty()) {
                        broadcastPurge(purgedMessageIds);
                    }
                });
        log.info(
                "IP banned: range={}, until={}, by={}",
                range.toCanonicalString(),
                until,
                createdBy);

        return ChatBannedIpResponse.from(saved);
    }

    /** 금칙어 보류 항목에서 작성자 IP 대역을 막는다. IPv6은 /64, IPv4는 주소 하나다. */
    @Transactional
    public ChatBannedIpResponse banWriterRange(
            String writerIp, String reason, LocalDateTime until, String createdBy) {
        IpRange address =
                IpRange.parse(writerIp)
                        .orElseThrow(() -> new CustomException(ErrorCode.IP_BAN_INVALID_RANGE));
        String range =
                address.isIpv6()
                        ? IpRange.parse(writerIp.strip() + "/" + HOLD_SHORTCUT_IPV6_PREFIX)
                                .orElseThrow()
                                .toCanonicalString()
                        : address.toCanonicalString();
        return banIp(range, reason, until, createdBy);
    }

    @Transactional
    public void unbanIp(Long id) {
        repository
                .findById(id)
                .ifPresent(
                        ban -> {
                            repository.delete(ban);
                            actionLogger.record(
                                    AdminActionType.CHAT_IP_UNBAN,
                                    AdminTargetType.CHAT_BAN,
                                    id,
                                    ban.getBannedIp());
                            // 같은 대역의 다른 차단이 남아 있으면 다시 읽을 때 그대로 남는다.
                            afterCommit(this::reload);
                            log.info("IP unbanned: range={}", ban.getBannedIp());
                        });
    }

    @Transactional
    public void banIpByMessageId(String messageId, int days, String createdBy) {
        String ip = historyBuffer.getIpForMessage(messageId);
        // IP를 모르거나 IP가 아닌 값이면 차단 대신 그 메시지만 지운다. banIp는 이런 값을 400으로 거절한다.
        if (IpRange.parse(ip).isEmpty()) {
            List<String> removed = historyBuffer.removeByMessageId(messageId);
            if (!removed.isEmpty()) {
                broadcastPurge(removed);
            }
            return;
        }
        banIp(
                ip,
                "관리자 메시지 신고 차단 " + days + "일 (" + messageId + ")",
                LocalDateTime.now().plusDays(days),
                createdBy);
    }

    /**
     * 메시지 한 줄만 가린다 (IKA-115). 차단까지는 과한 한 번의 실수를 지울 때 쓴다.
     *
     * <ul>
     *   <li>최근 대화에서 밀려난 메시지도 접속 중인 화면에는 남아 있을 수 있어, 버퍼에 없어도 지우라는 방송은 보낸다.
     *   <li>라이브톡은 본문을 저장하지 않으므로 조치 기록에는 메시지 ID와 닉네임만 남긴다. 가린 메시지는 되돌릴 수 없다.
     * </ul>
     */
    @Transactional
    public void hideMessage(String messageId) {
        if (messageId == null
                || messageId.isBlank()
                || messageId.length() > MAX_MESSAGE_ID_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String nickname =
                historyBuffer
                        .findMessage(messageId)
                        .map(
                                message ->
                                        message.sender() != null
                                                ? message.sender().nickname()
                                                : null)
                        .orElse(null);
        actionLogger.record(
                AdminActionType.CHAT_MESSAGE_HIDE,
                AdminTargetType.CHAT_MESSAGE,
                messageId,
                nickname != null ? nickname : "최근 대화에 없는 메시지");
        afterCommit(
                () -> {
                    historyBuffer.removeByMessageId(messageId);
                    broadcastPurge(List.of(messageId));
                });
        log.info("Chat message hidden: messageId={}", messageId);
    }

    private static IpRange validRange(String value) {
        IpRange range =
                IpRange.parse(value)
                        .orElseThrow(() -> new CustomException(ErrorCode.IP_BAN_INVALID_RANGE));
        int minimum = range.isIpv6() ? MIN_IPV6_PREFIX : MIN_IPV4_PREFIX;
        if (range.prefixLength() < minimum) {
            throw new CustomException(ErrorCode.IP_BAN_RANGE_TOO_WIDE);
        }
        return range;
    }

    /**
     * 실제로 비교할 대역. IPv6 주소 하나나 /64보다 좁은 대역을 막은 줄은 같은 /64 대역 전체에 적용한다. 주소 끝자리를 바꿔 가며 차단을 피하지 못하게 하려는
     * 것이다 (IKA-93). 저장 값은 입력한 주소 그대로 두고 비교할 때만 넓힌다.
     */
    private static IpRange effectiveRange(IpRange range) {
        if (!range.isIpv6() || range.prefixLength() <= HOLD_SHORTCUT_IPV6_PREFIX) return range;
        String address = range.toCanonicalString().split("/")[0];
        return IpRange.parse(address + "/" + HOLD_SHORTCUT_IPV6_PREFIX).orElse(range);
    }

    /** 트랜잭션 안이면 커밋 뒤에, 밖이면 바로 실행한다. */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                });
    }

    private void broadcastPurge(List<String> messageIds) {
        try {
            messagingTemplate.convertAndSend(
                    (String) "/sub/chat/main",
                    (Object)
                            Map.of(
                                    "type",
                                    "PURGE_MESSAGES",
                                    "messageIds",
                                    List.copyOf(messageIds)));
        } catch (Exception e) {
            log.warn("Failed to broadcast message purge: {}", e.getMessage());
        }
    }

    private record ActiveBan(IpRange range, LocalDateTime until) {
        boolean isActive(LocalDateTime now) {
            return until == null || until.isAfter(now);
        }
    }
}
