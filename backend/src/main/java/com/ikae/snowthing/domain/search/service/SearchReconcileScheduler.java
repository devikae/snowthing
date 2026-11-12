package com.ikae.snowthing.domain.search.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 하루 한 번 대조와 오래된 DONE 이벤트 정리. 여러 인스턴스가 함께 돌면 대조가 겹칠 수 있지만, PENDING 이벤트가 있는 원문은 다시 넣지 않고 처리기도 멱등이라
 * 결과는 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "snowthing.search.reconcile.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SearchReconcileScheduler {

    private final SearchConsistencyService consistency;

    @Scheduled(cron = "${snowthing.search.reconcile.cron:0 30 4 * * *}", zone = "Asia/Seoul")
    public void reconcileDaily() {
        // 대조가 실패해도 정리는 따로 실행한다. 정리가 막히면 outbox가 계속 커진다.
        try {
            consistency.reconcile();
        } catch (RuntimeException failure) {
            log.warn("search reconcile failed", failure);
        }
        try {
            int purged = consistency.purgeDoneEvents();
            log.info("search outbox done events purged: count={}", purged);
        } catch (RuntimeException failure) {
            log.warn("search outbox purge failed", failure);
        }
    }
}
