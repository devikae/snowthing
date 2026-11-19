package com.ikae.snowthing.domain.dmmail.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 쪽지 메일 폴링 워커. 운영은 서버 한 대에 이 워커 하나다. 여러 프로세스가 같은 DB를 봐도 받는 사람 상태 행의 SKIP LOCKED와 claim_token으로 같은
 * 받는 사람을 동시에 처리하지 않는다. {@code snowthing.dm-mail.send-enabled=false}면 빈을 만들지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "snowthing.dm-mail.send-enabled", havingValue = "true")
public class DmMailScheduler {

    private static final int MAX_POLLS_PER_TICK = 20;
    private static final int PURGE_LIMIT = 1000;

    private final DmMailProcessor processor;
    private final DmMailOutboxRepository outbox;
    private final DmMailProperties properties;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${snowthing.dm-mail.poll-interval-ms:1000}",
            initialDelayString = "${snowthing.dm-mail.initial-delay-ms:10000}")
    public void poll() {
        try {
            int polls = 0;
            while (processor.processBatch() > 0 && ++polls < MAX_POLLS_PER_TICK) {
                // 처리한 받는 사람이 있으면 바로 다음 배치를 본다.
            }
        } catch (RuntimeException failure) {
            log.warn("dm mail poll failed: error={}", failure.getClass().getSimpleName());
        }
    }

    /** 보관 기간이 지난 ACCEPTED, SKIPPED 행을 지운다. UNKNOWN, FAILED는 남긴다. */
    @Scheduled(cron = "${snowthing.dm-mail.purge-cron:0 40 4 * * *}")
    public void purge() {
        try {
            LocalDateTime before = LocalDateTime.now(clock).minus(properties.retention());
            int deleted = outbox.purgeFinished(before, PURGE_LIMIT);
            if (deleted > 0) {
                log.info("dm mail outbox purged: count={}", deleted);
            }
        } catch (RuntimeException failure) {
            log.warn("dm mail purge failed: error={}", failure.getClass().getSimpleName());
        }
    }
}
