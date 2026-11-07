package com.ikae.snowthing.domain.member.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.member.repository.WithdrawnEmailRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 기간이 지난 재가입 차단 해시를 지운다. 차단 해제는 조회 시각으로 판단하므로 이 작업은 보관 기간을 지키기 위한 것이다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WithdrawnEmailCleanupScheduler {

    static final int BATCH_SIZE = 1_000;
    static final int MAX_BATCHES = 100;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WithdrawnEmailRepository repository;
    private final Clock clock;

    @Scheduled(
            cron = "${snowthing.member.withdrawn-email-cleanup-cron:0 10 4 * * *}",
            zone = "Asia/Seoul")
    public void deleteExpired() {
        try {
            int deleted = deleteExpiredAt(LocalDateTime.now(clock.withZone(KST)));
            log.info("만료된 재가입 차단 해시 삭제 {}건", deleted);
        } catch (RuntimeException e) {
            log.warn("재가입 차단 해시 삭제 실패: {}", e.getClass().getSimpleName(), e);
        }
    }

    int deleteExpiredAt(LocalDateTime now) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            int deleted = repository.deleteExpired(now, BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }
        return total;
    }
}
