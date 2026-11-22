package com.ikae.snowthing.domain.moderation.service;

import java.time.Duration;

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

    /**
     * 한 번 깨어났을 때 쓰는 최대 시간. 스케줄러 스레드 풀(6개)을 다른 예약 작업과 함께 쓰므로, 2단계 응답이 느려도 한 틱이 길게 붙잡지 않게 묶음을 마칠 때마다
     * 확인한다. 마지막 묶음은 이 시간을 조금 넘길 수 있다. 남은 줄은 다음 틱이 가져간다.
     */
    static final Duration MAX_TICK_DURATION = Duration.ofSeconds(30);

    private final ModerationProcessor processor;

    @Scheduled(
            fixedDelayString = "${snowthing.moderation.poll-interval-ms:60000}",
            initialDelayString = "${snowthing.moderation.initial-delay-ms:30000}")
    public void poll() {
        try {
            long deadline = System.nanoTime() + MAX_TICK_DURATION.toNanos();
            int batches = 0;
            while (processor.processBatch() > 0
                    && ++batches < MAX_BATCHES_PER_TICK
                    && System.nanoTime() - deadline < 0) {
                // 가져온 줄이 있고 시간이 남았으면 바로 다음 묶음을 본다.
            }
        } catch (RuntimeException failure) {
            log.warn("content moderation poll failed: {}", failure.getClass().getSimpleName());
        }
    }
}
