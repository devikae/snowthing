package com.ikae.snowthing.domain.search.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * outbox 폴링. 여러 인스턴스가 동시에 돌아도 SKIP LOCKED와 lease로 같은 이벤트를 동시에 처리하지 않는다. {@code
 * snowthing.search.indexer.enabled=false}면 빈을 만들지 않는다(테스트는 끄고 처리기를 직접 부른다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "snowthing.search.indexer.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SearchOutboxScheduler {

    /** 한 번 깨어났을 때 연속으로 처리할 최대 배치 수. 밀린 이벤트를 비우되 스케줄러 스레드를 오래 잡지 않게 한다. */
    private static final int MAX_BATCHES_PER_TICK = 20;

    private final SearchOutboxProcessor processor;

    @Scheduled(
            fixedDelayString = "${snowthing.search.indexer.poll-interval-ms:1000}",
            initialDelayString = "${snowthing.search.indexer.initial-delay-ms:10000}")
    public void poll() {
        try {
            int batches = 0;
            while (processor.processBatch() > 0 && ++batches < MAX_BATCHES_PER_TICK) {
                // 처리한 이벤트가 있으면 바로 다음 배치를 본다.
            }
        } catch (RuntimeException failure) {
            log.warn("search outbox poll failed: error={}", failure.getClass().getSimpleName());
        }
    }
}
