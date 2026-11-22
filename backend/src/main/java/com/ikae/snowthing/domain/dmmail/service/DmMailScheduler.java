package com.ikae.snowthing.domain.dmmail.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.entity.DmMailSkipReason;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 쪽지 메일 폴링 워커. 운영은 서버 한 대에 이 워커 하나다. 여러 프로세스가 같은 DB를 봐도 받는 사람 상태 행의 SKIP LOCKED와 claim_token으로 같은
 * 받는 사람을 동시에 처리하지 않는다.
 *
 * <p>빈은 항상 뜨고, 돌 때마다 {@link DmMailSwitches#sendOn()}을 본다 (IKA-109). 관리자 화면에서 발송을 끄면 다음 주기부터 보내지 않고,
 * 켜면 재시작 없이 다시 보낸다.
 *
 * <ul>
 *   <li>꺼져 있는 동안: 매 주기 설정 캐시만 읽고 DB는 건드리지 않는다. 다만 정리 간격(기본 10분)마다 한 번, 발송 기한이 지난 대기 행을
 *       SKIPPED(SEND_OFF_EXPIRED)로 넘긴다. 기록만 켜 두고 발송을 오래 꺼 둬도 대기 행이 기한(24시간)치 넘게 쌓이지 않는다.
 *   <li>꺼짐에서 켜짐으로 바뀐 첫 주기(재시작 직후 포함): 발송 전에 같은 정리를 먼저 돌린다. 오래된 쪽지의 메일이 한꺼번에 나가지 않는다.
 *   <li>켜져 있는 동안: 기한 지난 행은 처리기가 발송 직전 확인에서 SKIPPED(EXPIRED)로 넘긴다.
 * </ul>
 */
@Slf4j
@Component
public class DmMailScheduler {

    private static final int MAX_POLLS_PER_TICK = 20;
    private static final int PURGE_LIMIT = 1000;
    private static final int MAX_PURGE_BATCHES = 20;
    private static final int SWEEP_LIMIT = 1000;
    private static final int MAX_SWEEP_BATCHES = 100;

    private final DmMailProcessor processor;
    private final DmMailOutboxRepository outbox;
    private final DmMailProperties properties;
    private final DmMailSwitches switches;
    private final Clock clock;
    private final Duration staleSweepInterval;

    /** 직전 주기에 발송이 켜져 있었는지. 스케줄러 스레드 하나만 읽고 쓴다. */
    private boolean sendingLastTick;

    private LocalDateTime lastSweepAt;

    public DmMailScheduler(
            DmMailProcessor processor,
            DmMailOutboxRepository outbox,
            DmMailProperties properties,
            DmMailSwitches switches,
            Clock clock,
            @Value("${snowthing.dm-mail.stale-sweep-interval:10m}") Duration staleSweepInterval) {
        this.processor = processor;
        this.outbox = outbox;
        this.properties = properties;
        this.switches = switches;
        this.clock = clock;
        this.staleSweepInterval = staleSweepInterval;
    }

    @Scheduled(
            fixedDelayString = "${snowthing.dm-mail.poll-interval-ms:1000}",
            initialDelayString = "${snowthing.dm-mail.initial-delay-ms:10000}")
    public void poll() {
        try {
            LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
            if (!switches.sendOn()) {
                sendingLastTick = false;
                if (lastSweepAt == null || !lastSweepAt.plus(staleSweepInterval).isAfter(now)) {
                    skipStale(now);
                }
                return;
            }
            if (!sendingLastTick) {
                skipStale(now);
                sendingLastTick = true;
            }
            int polls = 0;
            // 배치 사이에도 스위치를 다시 본다. 처리 중에 꺼지면 지금 받는 사람까지만 보내고 멈춘다.
            while (processor.processBatch() > 0
                    && ++polls < MAX_POLLS_PER_TICK
                    && switches.sendOn()) {
                // 처리한 받는 사람이 있으면 바로 다음 배치를 본다.
            }
        } catch (RuntimeException failure) {
            log.warn("dm mail poll failed: error={}", failure.getClass().getSimpleName());
        }
    }

    /** 발송 기한이 지난 대기 행을 SKIPPED(SEND_OFF_EXPIRED)로 넘긴다. */
    private void skipStale(LocalDateTime now) {
        lastSweepAt = now;
        LocalDateTime createdAtOrBefore = now.minus(properties.deadline());
        int total = 0;
        for (int batch = 0; batch < MAX_SWEEP_BATCHES; batch++) {
            int skipped =
                    outbox.skipStale(
                            createdAtOrBefore, DmMailSkipReason.SEND_OFF_EXPIRED, now, SWEEP_LIMIT);
            total += skipped;
            if (skipped < SWEEP_LIMIT) {
                break;
            }
        }
        if (total > 0) {
            log.info("dm mail stale rows skipped while sending was off: count={}", total);
        }
    }

    /** 보관 기간이 지난 ACCEPTED, SKIPPED 행을 지운다. UNKNOWN, FAILED는 남긴다. 발송 스위치와 관계없이 돈다. */
    @Scheduled(cron = "${snowthing.dm-mail.purge-cron:0 40 4 * * *}")
    public void purge() {
        try {
            LocalDateTime before = LocalDateTime.now(clock).minus(properties.retention());
            int total = 0;
            for (int batch = 0; batch < MAX_PURGE_BATCHES; batch++) {
                int deleted = outbox.purgeFinished(before, PURGE_LIMIT);
                total += deleted;
                if (deleted < PURGE_LIMIT) {
                    break;
                }
            }
            if (total > 0) {
                log.info("dm mail outbox purged: count={}", total);
            }
        } catch (RuntimeException failure) {
            log.warn("dm mail purge failed: error={}", failure.getClass().getSimpleName());
        }
    }
}
