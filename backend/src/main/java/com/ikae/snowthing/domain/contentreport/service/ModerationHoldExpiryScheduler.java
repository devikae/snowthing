package com.ikae.snowthing.domain.contentreport.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 임시조치는 법정 상한(30일) 안에서 끝나야 하므로 매시간 만료 건을 복구한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationHoldExpiryScheduler {

    private final ContentModerationService moderationService;

    @Scheduled(cron = "${snowthing.moderation.hold-expiry-cron:0 10 * * * *}", zone = "Asia/Seoul")
    public void releaseExpired() {
        try {
            int restored = moderationService.releaseExpiredHolds();
            if (restored > 0) {
                log.info("임시조치 기간 만료 복구 {}건", restored);
            }
        } catch (RuntimeException e) {
            log.warn("임시조치 만료 복구 실패: {}", e.getClass().getSimpleName(), e);
        }
    }
}
