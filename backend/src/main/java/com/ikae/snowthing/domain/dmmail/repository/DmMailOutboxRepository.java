package com.ikae.snowthing.domain.dmmail.repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.dmmail.entity.DmMailSkipReason;
import com.ikae.snowthing.domain.dmmail.entity.DmMailStatus;

/**
 * dm_mail_outbox, dm_mail_recipient_state 읽기와 쓰기. 쪽지 본문과 이메일 주소는 저장하지 않는다.
 *
 * <p>결과 기록 메서드는 모두 조건부 갱신(CAS)이다. {@code status = 'PROCESSING' AND claim_token = ?}이 맞는 행만 바꾸고, 바뀐
 * 행 수를 돌려준다. 선점이 만료돼 다른 판단이 내려진 뒤 늦게 돌아온 워커는 0을 받는다. 이 검사는 이미 시작한 SES 호출을 취소하지 못한다.
 */
@Repository
public class DmMailOutboxRepository {

    public static final String EVENT_TYPE = "DM_RECEIVED";
    public static final String CHANNEL = "EMAIL";

    private static final String OWNED =
            " WHERE event_id IN (:ids) AND status = 'PROCESSING' AND claim_token = :token";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public DmMailOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /** 쪽지 트랜잭션 안에서 부른다. 같은 쪽지의 발송 요청이 이미 있으면 아무것도 하지 않는다(유니크 제약). */
    public void insertIfAbsent(long directMessageId, long recipientId, LocalDateTime now) {
        jdbc.update(
                "INSERT INTO dm_mail_outbox (direct_message_id, recipient_member_id, event_type,"
                        + " channel, status, attempts, due_at, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE event_id = event_id",
                directMessageId,
                recipientId,
                EVENT_TYPE,
                CHANNEL,
                now,
                now,
                now);
    }

    /**
     * lease가 끝난 PROCESSING 행을 UNKNOWN으로 옮긴다. 워커가 SES를 부르기 전에 멈췄는지 후에 멈췄는지 알 수 없으므로 자동으로 다시 보내지 않는다.
     */
    public int expireLeases(LocalDateTime now) {
        return jdbc.update(
                "UPDATE dm_mail_outbox SET status = 'UNKNOWN', lease_until = NULL,"
                        + " last_error_code = 'LEASE_EXPIRED', updated_at = ?"
                        + " WHERE status = 'PROCESSING' AND lease_until <= ?",
                now,
                now);
    }

    /** 지금 보낼 수 있는 행이 있는 받는 사람. 가장 오래 기다린 사람부터 고른다. */
    public List<Long> findDueRecipients(LocalDateTime now, int limit) {
        return jdbc.queryForList(
                "SELECT recipient_member_id FROM dm_mail_outbox"
                        + " WHERE status IN ('PENDING', 'RETRY_WAIT') AND due_at <= ?"
                        + " GROUP BY recipient_member_id"
                        + " ORDER BY MIN(due_at), recipient_member_id LIMIT ?",
                Long.class,
                now,
                limit);
    }

    /**
     * 받는 사람 상태 행을 잠근다. 다른 워커가 잡고 있으면 기다리지 않고 비어 있는 값을 돌려준다. 호출하는 쪽 트랜잭션 안에서 실행해야 한다.
     *
     * <p>상태 행이 아직 없으면 만든 뒤 잠근다. 두 워커가 동시에 만들려 하면 한쪽의 INSERT IGNORE는 무시되고 다시 SKIP LOCKED로 확인해 건너뛴다.
     */
    public Optional<RecipientLock> lockRecipient(long recipientId) {
        Optional<RecipientLock> locked = selectForUpdateSkipLocked(recipientId);
        if (locked.isPresent()) {
            return locked;
        }
        Integer exists =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM dm_mail_recipient_state WHERE recipient_member_id = ?",
                        Integer.class,
                        recipientId);
        if (exists != null && exists > 0) {
            return Optional.empty();
        }
        jdbc.update(
                "INSERT IGNORE INTO dm_mail_recipient_state (recipient_member_id) VALUES (?)",
                recipientId);
        return selectForUpdateSkipLocked(recipientId);
    }

    private Optional<RecipientLock> selectForUpdateSkipLocked(long recipientId) {
        List<RecipientLock> rows =
                jdbc.query(
                        "SELECT last_attempt_at FROM dm_mail_recipient_state"
                                + " WHERE recipient_member_id = ? FOR UPDATE SKIP LOCKED",
                        (rs, rowNum) -> {
                            Timestamp last = rs.getTimestamp("last_attempt_at");
                            return new RecipientLock(
                                    recipientId, last == null ? null : last.toLocalDateTime());
                        },
                        recipientId);
        return rows.stream().findFirst();
    }

    /** 쿨다운 안이면 이 받는 사람의 대기 행을 쿨다운이 끝나는 시각으로 미룬다. */
    public int postpone(long recipientId, LocalDateTime dueAt, LocalDateTime now) {
        return jdbc.update(
                "UPDATE dm_mail_outbox SET due_at = ?, updated_at = ?"
                        + " WHERE recipient_member_id = ? AND status IN ('PENDING', 'RETRY_WAIT')"
                        + " AND due_at < ?",
                dueAt,
                now,
                recipientId,
                dueAt);
    }

    /**
     * 이 받는 사람의 보낼 수 있는 행을 모두 선점한다. 받는 사람 상태 행 잠금을 잡은 트랜잭션 안에서 부른다. attempts를 미리 올려 처리 도중 멈춰도 시도 횟수가
     * 남게 한다.
     */
    public List<ClaimedRow> claim(
            long recipientId, LocalDateTime now, String token, LocalDateTime leaseUntil) {
        List<ClaimedRow> rows =
                jdbc.query(
                        "SELECT event_id, direct_message_id, attempts FROM dm_mail_outbox"
                                + " WHERE recipient_member_id = ?"
                                + " AND status IN ('PENDING', 'RETRY_WAIT') AND due_at <= ?"
                                + " ORDER BY event_id FOR UPDATE",
                        (rs, rowNum) ->
                                new ClaimedRow(
                                        rs.getLong("event_id"),
                                        rs.getLong("direct_message_id"),
                                        rs.getInt("attempts") + 1),
                        recipientId,
                        now);
        if (rows.isEmpty()) {
            return rows;
        }
        named.update(
                "UPDATE dm_mail_outbox SET status = 'PROCESSING', claim_token = :token,"
                        + " lease_until = :leaseUntil, attempts = attempts + 1, updated_at = :now"
                        + " WHERE event_id IN (:ids)",
                Map.of(
                        "token",
                        token,
                        "leaseUntil",
                        leaseUntil,
                        "now",
                        now,
                        "ids",
                        rows.stream().map(ClaimedRow::eventId).toList()));
        return rows;
    }

    public void setLastAttempt(long recipientId, LocalDateTime at) {
        jdbc.update(
                "UPDATE dm_mail_recipient_state SET last_attempt_at = ? WHERE recipient_member_id = ?",
                at,
                recipientId);
    }

    /**
     * 메일이 나가지 않았음이 확실할 때 마지막 시도 시각을 선점 전 값으로 되돌린다. 재시도가 쿨다운에 막히지 않게 한다. 그 사이 다른 워커가 시각을 바꿨으면 건드리지
     * 않는다.
     */
    public void restoreLastAttempt(
            long recipientId, LocalDateTime claimedAt, LocalDateTime previous) {
        jdbc.update(
                "UPDATE dm_mail_recipient_state SET last_attempt_at = ?"
                        + " WHERE recipient_member_id = ? AND last_attempt_at = ?",
                previous,
                recipientId,
                claimedAt);
    }

    /** 발송 직전 확인에 필요한 값을 한 번에 읽는다. 쪽지 행이 없는 이벤트는 결과에 빠진다. */
    public List<MailTarget> loadTargets(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return List.of();
        }
        return named.query(
                "SELECT o.event_id, o.created_at, dm.read_at, sender.nickname AS sender_nickname,"
                        + " sender.status AS sender_status, receiver.email AS receiver_email,"
                        + " receiver.email_verified_at AS receiver_verified_at,"
                        + " receiver.status AS receiver_status, setting.dm_email_enabled"
                        + " FROM dm_mail_outbox o"
                        + " JOIN direct_message dm ON dm.direct_message_id = o.direct_message_id"
                        + " JOIN member sender ON sender.member_id = dm.sender_id"
                        + " JOIN member receiver ON receiver.member_id = o.recipient_member_id"
                        + " LEFT JOIN member_notification_setting setting"
                        + " ON setting.member_id = o.recipient_member_id"
                        + " WHERE o.event_id IN (:ids) ORDER BY o.event_id",
                Map.of("ids", eventIds),
                (rs, rowNum) -> {
                    Timestamp readAt = rs.getTimestamp("read_at");
                    Timestamp verifiedAt = rs.getTimestamp("receiver_verified_at");
                    return new MailTarget(
                            rs.getLong("event_id"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            readAt != null,
                            rs.getString("sender_nickname"),
                            rs.getString("sender_status"),
                            rs.getString("receiver_email"),
                            verifiedAt == null ? null : verifiedAt.toLocalDateTime(),
                            rs.getString("receiver_status"),
                            rs.getBoolean("dm_email_enabled"));
                });
    }

    public int markAccepted(
            Collection<Long> ids, String token, String providerMessageId, LocalDateTime now) {
        return named.update(
                "UPDATE dm_mail_outbox SET status = 'ACCEPTED', provider_message_id = :messageId,"
                        + " lease_until = NULL, last_error_code = NULL, updated_at = :now"
                        + OWNED,
                params(ids, token, now, Map.of("messageId", providerMessageId)));
    }

    public int markRetry(
            Collection<Long> ids,
            String token,
            LocalDateTime dueAt,
            String errorCode,
            LocalDateTime now) {
        return named.update(
                "UPDATE dm_mail_outbox SET status = 'RETRY_WAIT', due_at = :dueAt,"
                        + " lease_until = NULL, last_error_code = :code, updated_at = :now"
                        + OWNED,
                params(ids, token, now, Map.of("dueAt", dueAt, "code", errorCode)));
    }

    /** FAILED 또는 UNKNOWN으로 끝낸다. */
    public int markFinal(
            Collection<Long> ids,
            String token,
            DmMailStatus status,
            String errorCode,
            LocalDateTime now) {
        if (status != DmMailStatus.FAILED && status != DmMailStatus.UNKNOWN) {
            throw new IllegalArgumentException("final status must be FAILED or UNKNOWN");
        }
        return named.update(
                "UPDATE dm_mail_outbox SET status = :status, lease_until = NULL,"
                        + " last_error_code = :code, updated_at = :now"
                        + OWNED,
                params(ids, token, now, Map.of("status", status.name(), "code", errorCode)));
    }

    public int markSkipped(
            Collection<Long> ids, String token, DmMailSkipReason reason, LocalDateTime now) {
        return named.update(
                "UPDATE dm_mail_outbox SET status = 'SKIPPED', skip_reason = :reason,"
                        + " lease_until = NULL, updated_at = :now"
                        + OWNED,
                params(ids, token, now, Map.of("reason", reason.name())));
    }

    public Map<DmMailStatus, Long> countByStatus() {
        Map<DmMailStatus, Long> counts = new EnumMap<>(DmMailStatus.class);
        for (DmMailStatus status : DmMailStatus.values()) {
            counts.put(status, 0L);
        }
        jdbc.query(
                "SELECT status, COUNT(*) AS cnt FROM dm_mail_outbox GROUP BY status",
                rs -> {
                    counts.put(DmMailStatus.valueOf(rs.getString("status")), rs.getLong("cnt"));
                });
        return Collections.unmodifiableMap(counts);
    }

    /** 아직 끝나지 않은(PENDING, RETRY_WAIT, PROCESSING) 행 중 가장 오래된 생성 시각. 없으면 null. */
    public LocalDateTime findOldestOpenCreatedAt() {
        return jdbc.queryForObject(
                "SELECT MIN(created_at) FROM dm_mail_outbox"
                        + " WHERE status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING')",
                LocalDateTime.class);
    }

    /** 보관 기간이 지난 ACCEPTED, SKIPPED 행을 지운다. UNKNOWN, FAILED는 운영 확인용으로 남긴다. */
    public int purgeFinished(LocalDateTime before, int limit) {
        return jdbc.update(
                "DELETE FROM dm_mail_outbox WHERE status IN ('ACCEPTED', 'SKIPPED')"
                        + " AND updated_at < ? ORDER BY event_id LIMIT ?",
                before,
                limit);
    }

    private static Map<String, Object> params(
            Collection<Long> ids, String token, LocalDateTime now, Map<String, Object> extra) {
        Map<String, Object> params = new HashMap<>(extra);
        params.put("ids", ids);
        params.put("token", token);
        params.put("now", now);
        return params;
    }

    /**
     * @param lastAttemptAt 선점 전 마지막 SES 호출 시작 시각. 처음이면 null
     */
    public record RecipientLock(long recipientId, LocalDateTime lastAttemptAt) {}

    /**
     * @param attempt 이번 시도가 몇 번째인지(선점하면서 올린 값)
     */
    public record ClaimedRow(long eventId, long directMessageId, int attempt) {}

    public record MailTarget(
            long eventId,
            LocalDateTime createdAt,
            boolean read,
            String senderNickname,
            String senderStatus,
            String receiverEmail,
            LocalDateTime receiverEmailVerifiedAt,
            String receiverStatus,
            boolean dmEmailEnabled) {}
}
