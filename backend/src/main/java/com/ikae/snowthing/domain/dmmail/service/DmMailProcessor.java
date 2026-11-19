package com.ikae.snowthing.domain.dmmail.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.dmmail.config.DmMailProperties;
import com.ikae.snowthing.domain.dmmail.entity.DmMailSkipReason;
import com.ikae.snowthing.domain.dmmail.entity.DmMailStatus;
import com.ikae.snowthing.domain.dmmail.mail.DmMailResult;
import com.ikae.snowthing.domain.dmmail.mail.DmMailSender;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository.ClaimedRow;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository.MailTarget;
import com.ikae.snowthing.domain.dmmail.repository.DmMailOutboxRepository.RecipientLock;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.notification.service.MailDeliverability;

import lombok.extern.slf4j.Slf4j;

/**
 * dm_mail_outbox를 받는 사람 단위로 묶어 메일을 보낸다.
 *
 * <p>받는 사람 한 명마다 세 구간으로 나눈다.
 *
 * <ol>
 *   <li>선점 트랜잭션(밀리초): 받는 사람 상태 행을 SKIP LOCKED로 잠그고, 쿨다운 안이면 대기 행을 미루고 끝낸다. 아니면 보낼 수 있는 행을 모두
 *       PROCESSING으로 바꾸고 claim_token과 lease를 기록한 뒤 바로 커밋한다.
 *   <li>트랜잭션 없음: 발송 직전 확인 후 남은 쪽지로 메일 한 통을 만들어 SES를 한 번 부른다. SES가 느려도 DB 커넥션과 잠금을 쥐고 있지 않다.
 *   <li>결과 트랜잭션: claim_token이 맞는 행만 결과를 기록한다(CAS). 바뀐 행 수가 잡은 행 수와 다르면 늦은 워커로 보고 경고만 남긴다.
 * </ol>
 *
 * <p>받는 사람을 한 명씩 선점하고 보내므로 lease는 SES 호출 한 번만 감당하면 된다. 여러 명을 한꺼번에 잡고 차례로 보내면 뒤쪽 작업의 lease가 대기 중에
 * 줄어든다.
 *
 * <p>트랜잭션은 READ COMMITTED로 연다. 상태 행 잠금을 기다리는 동안 다른 트랜잭션이 커밋한 행을 보기 위해서다.
 */
@Slf4j
@Service
public class DmMailProcessor {

    private final DmMailOutboxRepository outbox;
    private final DmMailSender sender;
    private final DmMailComposer composer;
    private final DmMailProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public DmMailProcessor(
            DmMailOutboxRepository outbox,
            DmMailSender sender,
            DmMailComposer composer,
            DmMailProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.outbox = outbox;
        this.sender = sender;
        this.composer = composer;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** 폴링 한 번. 메일 처리를 마친 받는 사람 수를 돌려준다. */
    public int processBatch() {
        return processBatch(now());
    }

    /** 테스트가 시각을 정해 부를 수 있게 연 메서드. 배치 안의 판단은 이 시각을 기준으로 한다. */
    public int processBatch(LocalDateTime now) {
        if (!sender.enabled()) {
            return 0;
        }
        int expired = tx.execute(status -> outbox.expireLeases(now));
        if (expired > 0) {
            log.warn("dm mail lease expired, moved to UNKNOWN: count={}", expired);
        }
        List<Long> recipients =
                tx.execute(status -> outbox.findDueRecipients(now, properties.recipientsPerPoll()));
        int processed = 0;
        for (Long recipientId : recipients) {
            if (processRecipient(recipientId, now)) {
                processed++;
            }
        }
        return processed;
    }

    /** 받는 사람 한 명을 처리한다. 다른 워커가 잡고 있거나 쿨다운 안이면 false. */
    public boolean processRecipient(long recipientId, LocalDateTime now) {
        Claim claim = tx.execute(status -> claim(recipientId, now));
        if (claim == null) {
            return false;
        }
        List<Long> claimedIds = claim.rows().stream().map(ClaimedRow::eventId).toList();
        List<MailTarget> sendable = new ArrayList<>();
        Map<DmMailSkipReason, List<Long>> skipped = new EnumMap<>(DmMailSkipReason.class);
        DmMailResult result;
        try {
            List<MailTarget> targets = outbox.loadTargets(claimedIds);
            List<Long> found = targets.stream().map(MailTarget::eventId).toList();
            for (Long id : claimedIds) {
                if (!found.contains(id)) {
                    skipped.computeIfAbsent(
                                    DmMailSkipReason.MESSAGE_MISSING, k -> new ArrayList<>())
                            .add(id);
                }
            }
            for (MailTarget target : targets) {
                DmMailSkipReason reason = skipReason(target, now);
                if (reason == null) {
                    sendable.add(target);
                } else {
                    skipped.computeIfAbsent(reason, k -> new ArrayList<>()).add(target.eventId());
                }
            }
            result =
                    sendable.isEmpty()
                            ? null
                            : sender.send(
                                    composer.compose(sendable.get(0).receiverEmail(), sendable));
        } catch (RuntimeException failure) {
            // SES를 부르기 전에 실패했다. 메일은 나가지 않았으므로 재시도해도 중복이 생기지 않는다.
            log.warn(
                    "dm mail preparation failed: recipientId={}, error={}",
                    recipientId,
                    failure.getClass().getSimpleName());
            sendable.clear();
            skipped.clear();
            result = new DmMailResult.NotSent("PREPARE_" + failure.getClass().getSimpleName());
            List<ClaimedRow> all = claim.rows();
            DmMailResult prepareFailure = result;
            tx.executeWithoutResult(status -> record(claim, all, prepareFailure, Map.of(), now));
            return true;
        }
        List<ClaimedRow> sendRows =
                claim.rows().stream()
                        .filter(
                                row ->
                                        sendable.stream()
                                                .anyMatch(t -> t.eventId() == row.eventId()))
                        .toList();
        DmMailResult sent = result;
        tx.executeWithoutResult(status -> record(claim, sendRows, sent, skipped, now));
        return true;
    }

    private Claim claim(long recipientId, LocalDateTime now) {
        RecipientLock lock = outbox.lockRecipient(recipientId).orElse(null);
        if (lock == null) {
            return null;
        }
        LocalDateTime last = lock.lastAttemptAt();
        if (last != null) {
            LocalDateTime cooldownEnds = last.plus(properties.cooldown());
            if (cooldownEnds.isAfter(now)) {
                outbox.postpone(recipientId, cooldownEnds, now);
                return null;
            }
        }
        String token = UUID.randomUUID().toString();
        List<ClaimedRow> rows = outbox.claim(recipientId, now, token, now.plus(properties.lease()));
        if (rows.isEmpty()) {
            return null;
        }
        outbox.setLastAttempt(recipientId, now);
        return new Claim(recipientId, token, rows, last, now);
    }

    /** 판단 순서는 {@link DmMailSkipReason} 선언 순서와 같다. 보내도 되면 null. */
    private DmMailSkipReason skipReason(MailTarget target, LocalDateTime now) {
        if (!target.createdAt().plus(properties.deadline()).isAfter(now)) {
            return DmMailSkipReason.EXPIRED;
        }
        if (!MemberStatus.ACTIVE.name().equals(target.receiverStatus())) {
            return DmMailSkipReason.RECIPIENT_INACTIVE;
        }
        if (!target.dmEmailEnabled()) {
            return DmMailSkipReason.NOTIFICATION_OFF;
        }
        if (!MailDeliverability.isDeliverable(
                target.receiverEmail(), target.receiverEmailVerifiedAt())) {
            return DmMailSkipReason.EMAIL_UNDELIVERABLE;
        }
        if (target.read()) {
            return DmMailSkipReason.ALREADY_READ;
        }
        return null;
    }

    private void record(
            Claim claim,
            List<ClaimedRow> sendRows,
            DmMailResult result,
            Map<DmMailSkipReason, List<Long>> skipped,
            LocalDateTime now) {
        int expected = sendRows.size();
        int changed = 0;
        for (Map.Entry<DmMailSkipReason, List<Long>> entry : skipped.entrySet()) {
            expected += entry.getValue().size();
            changed += outbox.markSkipped(entry.getValue(), claim.token(), entry.getKey(), now);
        }
        List<Long> sendIds = sendRows.stream().map(ClaimedRow::eventId).toList();
        boolean mailMayHaveGone = false;
        if (result instanceof DmMailResult.Accepted accepted) {
            changed += outbox.markAccepted(sendIds, claim.token(), accepted.messageId(), now);
            mailMayHaveGone = true;
            log.info("dm mail accepted: recipientId={}, events={}", claim.recipientId(), sendIds);
        } else if (result instanceof DmMailResult.Unknown unknown) {
            changed +=
                    outbox.markFinal(
                            sendIds, claim.token(), DmMailStatus.UNKNOWN, unknown.code(), now);
            mailMayHaveGone = true;
            log.warn(
                    "dm mail outcome unknown, not retrying: recipientId={}, events={}, code={}",
                    claim.recipientId(),
                    sendIds,
                    unknown.code());
        } else if (result instanceof DmMailResult.Rejected rejected) {
            changed +=
                    outbox.markFinal(
                            sendIds, claim.token(), DmMailStatus.FAILED, rejected.code(), now);
            log.warn(
                    "dm mail rejected: recipientId={}, events={}, code={}",
                    claim.recipientId(),
                    sendIds,
                    rejected.code());
        } else if (result instanceof DmMailResult.NotSent notSent) {
            changed += recordNotSent(claim, sendRows, notSent.code(), now);
        }
        if (!mailMayHaveGone) {
            // 메일이 나가지 않았음이 확실하다. 다음 시도가 쿨다운에 막히지 않게 되돌린다.
            outbox.restoreLastAttempt(
                    claim.recipientId(), claim.claimedAt(), claim.previousAttemptAt());
        }
        if (changed != expected) {
            log.warn(
                    "dm mail result not recorded for stale claim: recipientId={}, expected={}, changed={}",
                    claim.recipientId(),
                    expected,
                    changed);
        }
    }

    private int recordNotSent(Claim claim, List<ClaimedRow> rows, String code, LocalDateTime now) {
        List<Long> exhausted = new ArrayList<>();
        int changed = 0;
        for (ClaimedRow row : rows) {
            if (row.attempt() >= properties.maxAttempts()) {
                exhausted.add(row.eventId());
            } else {
                changed +=
                        outbox.markRetry(
                                List.of(row.eventId()),
                                claim.token(),
                                now.plus(backoff(row.attempt())),
                                code,
                                now);
            }
        }
        if (!exhausted.isEmpty()) {
            changed +=
                    outbox.markFinal(
                            exhausted,
                            claim.token(),
                            DmMailStatus.FAILED,
                            "RETRY_EXHAUSTED_" + code,
                            now);
        }
        return changed;
    }

    /**
     * 지수 백오프에 지터를 더한다. 백오프 값의 절반에서 전체 사이를 무작위로 고른다(equal jitter). 여러 워커가 같은 간격으로 몰려 다시 부르지 않게 하면서,
     * 0에 가까운 대기는 피한다.
     */
    Duration backoff(int attempt) {
        long base = properties.retryBaseDelay().toMillis();
        long cap = properties.retryMaxDelay().toMillis();
        long exponential = Math.min(cap, base * (1L << Math.min(Math.max(attempt - 1, 0), 20)));
        long jittered = ThreadLocalRandom.current().nextLong(exponential / 2, exponential + 1);
        return Duration.ofMillis(jittered);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private record Claim(
            long recipientId,
            String token,
            List<ClaimedRow> rows,
            LocalDateTime previousAttemptAt,
            LocalDateTime claimedAt) {}
}
