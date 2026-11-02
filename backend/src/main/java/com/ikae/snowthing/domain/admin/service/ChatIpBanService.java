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

import com.ikae.snowthing.domain.admin.dto.ChatBannedIpResponse;
import com.ikae.snowthing.domain.admin.entity.ChatBannedIp;
import com.ikae.snowthing.domain.admin.repository.ChatBannedIpRepository;
import com.ikae.snowthing.domain.chat.service.ChatRecentHistoryBuffer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ChatIpBanService {

    private final ChatBannedIpRepository repository;
    private final ChatRecentHistoryBuffer historyBuffer;
    private final SimpMessagingTemplate messagingTemplate;

    // In-memory cache: ip -> bannedUntil (null / MAX means permanent)
    private final ConcurrentHashMap<String, LocalDateTime> activeBans = new ConcurrentHashMap<>();

    public ChatIpBanService(
            ChatBannedIpRepository repository,
            ChatRecentHistoryBuffer historyBuffer,
            @Lazy SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.historyBuffer = historyBuffer;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        List<ChatBannedIp> bans = repository.findAllActiveBans(LocalDateTime.now());
        for (ChatBannedIp ban : bans) {
            activeBans.put(
                    ban.getBannedIp(),
                    ban.getBannedUntil() == null ? LocalDateTime.MAX : ban.getBannedUntil());
        }
        log.info("Loaded {} active chat IP bans into memory", activeBans.size());
    }

    public boolean isBanned(String ip) {
        if (ip == null || ip.isBlank()) return false;
        LocalDateTime until = activeBans.get(ip);
        if (until == null) return false;
        if (until.isBefore(LocalDateTime.now())) {
            activeBans.remove(ip);
            return false;
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<ChatBannedIpResponse> getBannedIps() {
        return List.copyOf(
                repository.findAllByOrderByCreatedAtDesc().stream()
                        .map(ChatBannedIpResponse::from)
                        .toList());
    }

    @Transactional
    public ChatBannedIpResponse banIp(
            String ip, String reason, LocalDateTime until, String createdBy) {
        ChatBannedIp entity =
                ChatBannedIp.builder()
                        .bannedIp(ip)
                        .reason(reason)
                        .bannedUntil(until)
                        .createdBy(createdBy)
                        .build();

        ChatBannedIp saved = repository.save(entity);
        activeBans.put(ip, until == null ? LocalDateTime.MAX : until);
        log.info("Chat IP banned: ip={}, until={}, by={}", ip, until, createdBy);

        List<String> purgedMessageIds = historyBuffer.removeByIp(ip);
        if (!purgedMessageIds.isEmpty()) {
            broadcastPurge(purgedMessageIds);
        }

        return ChatBannedIpResponse.from(saved);
    }

    @Transactional
    public void unbanIp(Long id) {
        repository
                .findById(id)
                .ifPresent(
                        ban -> {
                            repository.delete(ban);
                            activeBans.remove(ban.getBannedIp());
                            log.info("Chat IP unbanned: ip={}", ban.getBannedIp());
                        });
    }

    @Transactional
    public void banIpByMessageId(String messageId, String createdBy) {
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
                "관리자 메시지 신고 차단 (" + messageId + ")",
                LocalDateTime.now().plusDays(7),
                createdBy);
    }

    private void broadcastPurge(List<String> messageIds) {
        try {
            messagingTemplate.convertAndSend((String) "/sub/chat/main", (Object) Map.of("type", "PURGE_MESSAGES", "messageIds", List.copyOf(messageIds)));
        } catch (Exception e) {
            log.warn("Failed to broadcast message purge: {}", e.getMessage());
        }
    }
}
