package com.ikae.snowthing.domain.chat.service;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;

/**
 * 실시간 라이브톡 최근 대화 인메모리 순환 버퍼 (Circular Ring Buffer). 새로고침 시 최대 30개의 최근 대화 내역을 즉시 제공하여 대화 맥락 단절을
 * 방지합니다.
 */
@Component
public class ChatRecentHistoryBuffer {

    public static final int MAX_RECENT_MESSAGES = 30;

    private final ConcurrentLinkedDeque<ChatMessageResponse> buffer = new ConcurrentLinkedDeque<>();

    public void append(ChatMessageResponse message) {
        if (message == null) {
            return;
        }
        buffer.addLast(message);
        while (buffer.size() > MAX_RECENT_MESSAGES) {
            buffer.pollFirst();
        }
    }

    public List<ChatMessageResponse> getRecentMessages() {
        return List.copyOf(buffer); // Rule 22 방어적 복사 불변 보장
    }

    public void clear() {
        buffer.clear();
    }
}
