package com.ikae.snowthing.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.dto.SenderDto;

class ChatRecentHistoryBufferTest {

    private ChatRecentHistoryBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new ChatRecentHistoryBuffer();
    }

    @Test
    @DisplayName("최근 30개 이내의 메시지는 순서대로 모두 유지된다")
    void appendWithinLimit() {
        for (int i = 1; i <= 20; i++) {
            buffer.append(createMessage("메시지 " + i));
        }

        List<ChatMessageResponse> recent = buffer.getRecentMessages();
        assertThat(recent).hasSize(20);
        assertThat(recent.get(0).content()).isEqualTo("메시지 1");
        assertThat(recent.get(19).content()).isEqualTo("메시지 20");
    }

    @Test
    @DisplayName("30개를 초과하여 추가되면 가장 오래된 메시지가 방출되고 최근 30개만 유지된다")
    void evictOldestWhenExceedingLimit() {
        for (int i = 1; i <= 40; i++) {
            buffer.append(createMessage("메시지 " + i));
        }

        List<ChatMessageResponse> recent = buffer.getRecentMessages();
        assertThat(recent).hasSize(30);
        // 1~10번은 방출되고 11~40번이 남아야 함
        assertThat(recent.get(0).content()).isEqualTo("메시지 11");
        assertThat(recent.get(29).content()).isEqualTo("메시지 40");
    }

    @Test
    @DisplayName("반환된 최근 메시지 리스트는 불변이며 외부 수정을 차단한다 (Rule 22)")
    void immutabilityGuaranteed() {
        buffer.append(createMessage("테스트"));
        List<ChatMessageResponse> recent = buffer.getRecentMessages();

        assertThatThrownBy(() -> recent.add(createMessage("임의추가")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("다중 스레드 동시 추가 상황에서도 크기 30 상한을 초과하지 않는다")
    void concurrentAppendsMaintainSizeLimit() throws InterruptedException {
        int threads = 10;
        int messagesPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(
                    () -> {
                        try {
                            for (int m = 0; m < messagesPerThread; m++) {
                                buffer.append(createMessage("스레드 " + threadId + "-" + m));
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
        }

        latch.await();
        executor.shutdown();

        assertThat(buffer.getRecentMessages()).hasSize(30);
    }

    private ChatMessageResponse createMessage(String content) {
        return new ChatMessageResponse(
                UUID.randomUUID().toString(),
                new SenderDto("usr_test", "테스터"),
                "PHOENIX",
                content,
                LocalDateTime.now().toString());
    }
}
