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
 *   <li>저장은 기존 chat_banned_ip 표를 그대로 쓴다. 예전 단일 IP 차단은 /32, /128 대역으로 읽혀 계속 동작한다.
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
        reloadAfterCommit();
        log.info(
                "IP banned: range={}, until={}, by={}",
                range.toCanonicalString(),
                until,
                createdBy);

        List<String> purgedMessageIds = historyBuffer.removeByIpMatching(range::contains);
        if (!purgedMessageIds.isEmpty()) {
            broadcastPurge(purgedMessageIds);
        }

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
                            reloadAfterCommit();
                            log.info("IP unbanned: range={}", ban.getBannedIp());
                        });
    }

    @Transactional
    public void banIpByMessageId(String messageId, String createdBy) {
        banIpByMessageId(messageId, 7, createdBy);
    }

    @Transactional
    public void banIpByMessageId(String messageId, int days, String createdBy) {
        String ip = historyBuffer.getIpForMessage(messageId);
        if (ip == null || ip.isBlank()) {
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

    private void reloadAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            reload();
                        }
                    });
            return;
        }
        reload();
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
