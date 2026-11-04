package com.ikae.snowthing.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.notification.repository.NotificationRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 매일 04:00 KST에 만든 지 90일이 지난 알림을 지운다. 한 번에 {@value #BATCH_SIZE}건씩, 배치마다 따로 커밋해 긴 락을 피한다.
 *
 * <p>created_at은 KST LocalDateTime으로 저장된다(운영 TZ=Asia/Seoul). 단일 인스턴스 기준이다.
 */
@Slf4j
@Component
public class NotificationCleanupScheduler {

    static final int RETENTION_DAYS = 90;
    static final int BATCH_SIZE = 1_000;
    static final int MAX_BATCHES = 200;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Autowired
    public NotificationCleanupScheduler(NotificationRepository notificationRepository) {
        this(notificationRepository, Clock.system(KST));
    }

    NotificationCleanupScheduler(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${snowthing.notification.cleanup-cron:0 0 4 * * *}", zone = "Asia/Seoul")
    public void deleteExpired() {
        try {
            int deleted = deleteCreatedBefore(LocalDateTime.now(clock.withZone(KST)));
            log.info("만료 알림 삭제 {}건", deleted);
        } catch (RuntimeException e) {
            log.warn("만료 알림 삭제 실패: {}", e.getClass().getSimpleName(), e);
        }
    }

    /**
     * {@code now} 기준 보관 기간이 지난 알림을 지우고 지운 건수를 돌려준다. 남은 행이 많아도 {@value #MAX_BATCHES}번에서 멈추고 다음 날 이어서
     * 지운다.
     */
    int deleteCreatedBefore(LocalDateTime now) {
        LocalDateTime cutoff = now.minusDays(RETENTION_DAYS);
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int deleted = notificationRepository.deleteCreatedBefore(cutoff, BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }
}
