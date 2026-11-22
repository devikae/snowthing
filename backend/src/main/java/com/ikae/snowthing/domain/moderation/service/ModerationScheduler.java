package com.ikae.snowthing.domain.moderation.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 1분마다 검사 대기 줄을 묶어 처리한다 (IKA-80). 운영은 서버 한 대지만, 여러 대가 돌아도 SKIP LOCKED와 임대로 같은 줄을 동시에 처리하지 않는다. 기능이
 * 꺼져 있거나({@code snowthing.moderation.enabled=false}) 테스트처럼 스케줄러를 끄면 빈을 만들지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "${snowthing.moderation.enabled:false} and ${snowthing.moderation.scheduler-enabled:true}")
public class ModerationScheduler {

    /** 한 번 깨어났을 때 연속으로 처리할 최대 묶음 수. 밀린 줄을 비우되 스케줄러 스레드를 오래 잡지 않게 한다. */
    private static final int MAX_BATCHES_PER_TICK = 10;

    private final ModerationProcessor processor;

    @Scheduled(
            fixedDelayString = "${snowthing.moderation.poll-interval-ms:60000}",
            initialDelayString = "${snowthing.moderation.initial-delay-ms:30000}")
    public void poll() {
        try {
            int batches = 0;
            while (processor.processBatch() > 0 && ++batches < MAX_BATCHES_PER_TICK) {
                // 가져온 줄이 있으면 바로 다음 묶음을 본다.
            }
        } catch (RuntimeException failure) {
            log.warn("content moderation poll failed: {}", failure.getClass().getSimpleName());
        }
    }
}
