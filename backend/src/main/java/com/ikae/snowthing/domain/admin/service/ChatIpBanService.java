package com.ikae.snowthing.domain.admin.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatIpBanService {

    private final ChatBannedIpRepository repository;
    private final ChatRecentHistoryBuffer historyBuffer;
    private final SimpMessagingTemplate messagingTemplate;
    private final AdminActionLogger actionLogger;

    // 메모리 캐시: 정규화한 IP 키 -> 차단 끝 시각(영구는 MAX).
    // 키는 ClientIpResolver.normalizeForLimit 값이라 IPv6는 /64 대역으로 묶인다. 주소 끝자리가 바뀌어도 같은 차단에 걸린다.
    // DB에는 관리자가 입력한 원문을 그대로 두고 읽을 때 정규화한다. 그래서 예전에 원문으로 저장된 행도 그대로 맞는다.
    private final ConcurrentHashMap<String, LocalDateTime> activeBans = new ConcurrentHashMap<>();

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
        List<ChatBannedIp> bans = repository.findAllActiveBans(LocalDateTime.now());
        for (ChatBannedIp ban : bans) {
            String key = banKey(ban.getBannedIp());
            if (key != null) activeBans.merge(key, untilOf(ban), ChatIpBanService::later);
        }
        log.info("Loaded {} active chat IP bans into memory", activeBans.size());
    }

    public boolean isBanned(String ip) {
        String key = banKey(ip);
        if (key == null) return false;
        LocalDateTime until = activeBans.get(key);
        if (until == null) return false;
        if (until.isBefore(LocalDateTime.now())) {
            activeBans.remove(key, until);
            return false;
        }
        return true;
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
            String ip, String reason, LocalDateTime until, String createdBy) {
        String bannedIp = ip == null ? null : ip.trim();
        String key = banKey(bannedIp);
        // IP가 아닌 값을 차단하면 정규화 키가 unknown 하나로 모여 엉뚱한 사용자들이 함께 막힐 수 있어 받지 않는다.
        if (key == null) throw new CustomException(ErrorCode.INVALID_INPUT);
        ChatBannedIp entity =
                ChatBannedIp.builder()
                        .bannedIp(bannedIp)
                        .reason(reason)
                        .bannedUntil(until)
                        .createdBy(createdBy)
                        .build();

        ChatBannedIp saved = repository.save(entity);
        actionLogger.record(
                AdminActionType.CHAT_IP_BAN, AdminTargetType.CHAT_BAN, saved.getId(), reason);
        LocalDateTime effectiveUntil = until == null ? LocalDateTime.MAX : until;
        // 메모리 반영과 메시지 삭제는 커밋 뒤에 한다. 롤백된 차단이 메모리에 남아 사용자를 막지 않게 하려는 것이다.
        afterCommit(
                () -> {
                    activeBans.merge(key, effectiveUntil, ChatIpBanService::later);
                    List<String> purgedMessageIds = historyBuffer.removeByIp(bannedIp);
                    if (!purgedMessageIds.isEmpty()) {
                        broadcastPurge(purgedMessageIds);
                    }
                });
        log.info("Chat IP banned: ip={}, until={}, by={}", bannedIp, until, createdBy);

        return ChatBannedIpResponse.from(saved);
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
                                    null);
                            String key = banKey(ban.getBannedIp());
                            if (key != null) {
                                // 같은 대역의 다른 차단이 아직 살아 있으면 그 끝 시각으로 남긴다.
                                LocalDateTime remaining =
                                        repository.findAllActiveBans(LocalDateTime.now()).stream()
                                                .filter(other -> !id.equals(other.getId()))
                                                .filter(
                                                        other ->
                                                                key.equals(
                                                                        banKey(
                                                                                other
                                                                                        .getBannedIp())))
                                                .map(ChatIpBanService::untilOf)
                                                .reduce(ChatIpBanService::later)
                                                .orElse(null);
                                afterCommit(
                                        () -> {
                                            if (remaining == null) activeBans.remove(key);
                                            else activeBans.put(key, remaining);
                                        });
                            }
                            log.info("Chat IP unbanned: ip={}", ban.getBannedIp());
                        });
    }

    @Transactional
    public void banIpByMessageId(String messageId, int days, String createdBy) {
        String ip = historyBuffer.getIpForMessage(messageId);
        // IP를 모르거나 IP가 아닌 값이면 차단 대신 그 메시지만 지운다. banIp는 이런 값을 400으로 거절한다.
        if (banKey(ip) == null) {
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

    /** 차단 비교에 쓰는 키. IP가 아니면 null이다. */
    private static String banKey(String ip) {
        String key = ClientIpResolver.normalizeForLimit(ip == null ? null : ip.trim());
        return "unknown".equals(key) ? null : key;
    }

    private static LocalDateTime untilOf(ChatBannedIp ban) {
        return ban.getBannedUntil() == null ? LocalDateTime.MAX : ban.getBannedUntil();
    }

    /** 같은 키(IPv6는 같은 /64)에 차단이 겹치면 더 늦게 끝나는 차단을 따른다. 짧은 기간으로 다시 차단해도 남아 있는 긴 차단이 줄어들지 않는다. */
    private static LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        return left.isAfter(right) ? left : right;
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
}
