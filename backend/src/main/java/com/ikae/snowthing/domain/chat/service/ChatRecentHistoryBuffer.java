package com.ikae.snowthing.domain.chat.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;

/**
 * 실시간 라이브톡 최근 대화 인메모리 순환 버퍼 (Circular Ring Buffer). 새로고침 시 최대 30개의 최근 대화 내역을 즉시 제공하여 대화 맥락 단절을
 * 방지합니다.
 */
@Component
public class ChatRecentHistoryBuffer {

    public static final int MAX_RECENT_MESSAGES = 30;

    private final ConcurrentLinkedDeque<ChatMessageResponse> buffer = new ConcurrentLinkedDeque<>();
    private final Cache<String, String> messageIpCache =
            Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofHours(2)).build();

    public synchronized void append(ChatMessageResponse message) {
        append(message, null);
    }

    public synchronized void append(ChatMessageResponse message, String clientIp) {
        if (message == null) {
            return;
        }
        if (clientIp != null) {
            messageIpCache.put(message.messageId(), clientIp);
        }
        buffer.addLast(message);
        while (buffer.size() > MAX_RECENT_MESSAGES) {
            buffer.pollFirst();
        }
    }

    public String getIpForMessage(String messageId) {
        return messageIpCache.getIfPresent(messageId);
    }

    public synchronized List<String> removeByIp(String targetIp) {
        if (targetIp == null) return List.of();
        List<String> removed = new ArrayList<>();
        buffer.removeIf(
                m -> {
                    String ip = messageIpCache.getIfPresent(m.messageId());
                    if (targetIp.equals(ip)) {
                        removed.add(m.messageId());
                        return true;
                    }
                    return false;
                });
        return List.copyOf(removed);
    }

    public synchronized List<String> removeByMessageId(String messageId) {
        if (messageId == null) return List.of();
        List<String> removed = new ArrayList<>();
        buffer.removeIf(
                m -> {
                    if (messageId.equals(m.messageId())) {
                        removed.add(m.messageId());
                        return true;
                    }
                    return false;
                });
        return List.copyOf(removed);
    }

    public synchronized List<ChatMessageResponse> getRecentMessages() {
        return List.copyOf(buffer); // Rule 22 방어적 복사 불변 보장
    }

    /** 탈퇴한 회원의 닉네임이 새로고침 시 내려가는 최근 대화에 남지 않게 지운다. */
    public synchronized void removeSender(String publicId) {
        buffer.removeIf(
                message ->
                        message.sender() != null && publicId.equals(message.sender().publicId()));
    }

    public synchronized void clear() {
        buffer.clear();
    }
}
