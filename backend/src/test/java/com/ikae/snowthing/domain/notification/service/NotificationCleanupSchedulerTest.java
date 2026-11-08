package com.ikae.snowthing.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.notification.repository.NotificationRepository;

class NotificationCleanupSchedulerTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationCleanupScheduler scheduler =
            new NotificationCleanupScheduler(repository, Clock.systemUTC());

    @Test
    @DisplayName("90일 전을 기준으로 배치가 가득 차지 않을 때까지 반복해 지운다")
    void repeatsUntilPartialBatch() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 5, 4, 0);
        given(repository.deleteCreatedBefore(any(), anyInt()))
                .willReturn(
                        NotificationCleanupScheduler.BATCH_SIZE,
                        NotificationCleanupScheduler.BATCH_SIZE,
                        7);

        int deleted = scheduler.deleteCreatedBefore(now);

        assertThat(deleted).isEqualTo(NotificationCleanupScheduler.BATCH_SIZE * 2 + 7);
        verify(repository, times(3))
                .deleteCreatedBefore(
                        eq(LocalDateTime.of(2026, 8, 7, 4, 0)),
                        eq(NotificationCleanupScheduler.BATCH_SIZE));
    }

    @Test
    @DisplayName("지울 행이 계속 남아도 최대 배치 수에서 멈춘다")
    void stopsAtMaxBatches() {
        given(repository.deleteCreatedBefore(any(), anyInt()))
                .willReturn(NotificationCleanupScheduler.BATCH_SIZE);

        scheduler.deleteCreatedBefore(LocalDateTime.of(2026, 11, 5, 4, 0));

        verify(repository, times(NotificationCleanupScheduler.MAX_BATCHES))
                .deleteCreatedBefore(any(), anyInt());
    }
}
