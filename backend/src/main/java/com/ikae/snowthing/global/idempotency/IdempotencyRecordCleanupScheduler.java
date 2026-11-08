package com.ikae.snowthing.global.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 보관 기간(24시간)이 지난 Idempotency-Key 기록을 지운다.
 *
 * <p>재시도는 길어야 몇 분 안에 일어나므로 하루면 충분하다. 지난 기록이 지워진 뒤 같은 키가 오면 새 요청으로 처리된다. 한 번에 많이 지우면 잠금이 길어지므로
 * 1,000건씩 나눠 짧은 트랜잭션으로 지운다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyRecordCleanupScheduler {

    static final Duration RETENTION = Duration.ofHours(24);
    static final int BATCH_SIZE = 1000;
    static final int MAX_BATCHES = 50;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final IdempotencyRecordRepository records;
    private final Clock clock;

    @Scheduled(cron = "${snowthing.idempotency.cleanup-cron:0 35 * * * *}", zone = "Asia/Seoul")
    public void deleteExpired() {
        int deleted = deleteExpiredBefore(LocalDateTime.now(clock.withZone(KST)).minus(RETENTION));
        if (deleted > 0) {
            log.info("만료된 Idempotency-Key 기록 {}건 삭제", deleted);
        }
    }

    int deleteExpiredBefore(LocalDateTime cutoff) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int deleted = records.deleteCreatedBefore(cutoff, BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }
}
