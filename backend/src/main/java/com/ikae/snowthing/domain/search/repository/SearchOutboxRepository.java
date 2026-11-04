package com.ikae.snowthing.domain.search.repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.search.entity.SearchSourceType;

/** search_outbox 읽기·쓰기. 원문 내용은 다루지 않는다. */
@Repository
public class SearchOutboxRepository {

    public enum Status {
        PENDING,
        DONE,
        FAILED
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public SearchOutboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /**
     * 처리할 이벤트를 고르고 임대(lease)를 건다. 호출하는 쪽 트랜잭션 안에서 실행해야 한다.
     *
     * <p>FOR UPDATE SKIP LOCKED로 다른 인스턴스가 지금 고르고 있는 행은 건너뛴다. 고른 행에는 lease_until을 걸고 attempts를 미리 1
     * 올린다. 처리 도중 프로세스가 죽어도 시도 횟수가 남아 무한 재시도가 되지 않고, lease가 끝나면 다른 인스턴스가 다시 가져간다.
     */
    public List<ClaimedEvent> claim(LocalDateTime now, LocalDateTime leaseUntil, int limit) {
        List<ClaimedEvent> events =
                jdbc.query(
                        "SELECT event_id, source_type, source_id, source_version, attempts"
                                + " FROM search_outbox"
                                + " WHERE status = 'PENDING' AND due_at <= ?"
                                + " AND (lease_until IS NULL OR lease_until <= ?)"
                                + " ORDER BY due_at, event_id LIMIT ? FOR UPDATE SKIP LOCKED",
                        (rs, rowNum) ->
                                new ClaimedEvent(
                                        rs.getLong("event_id"),
                                        SearchSourceType.valueOf(rs.getString("source_type")),
                                        rs.getLong("source_id"),
                                        rs.getLong("source_version"),
                                        rs.getInt("attempts") + 1),
                        now,
                        now,
                        limit);
        if (events.isEmpty()) {
            return events;
        }
        named.update(
                "UPDATE search_outbox SET lease_until = :leaseUntil, attempts = attempts + 1"
                        + " WHERE event_id IN (:ids)",
                Map.of(
                        "leaseUntil",
                        leaseUntil,
                        "ids",
                        events.stream().map(ClaimedEvent::eventId).toList()));
        return events;
    }

    /** 색인 반영과 같은 트랜잭션에서 부른다. 이미 다른 처리기가 끝낸 이벤트면 0을 돌려준다. */
    public int markDone(long eventId, LocalDateTime now) {
        return jdbc.update(
                "UPDATE search_outbox SET status = 'DONE', processed_at = ?, lease_until = NULL,"
                        + " last_error_code = NULL WHERE event_id = ? AND status = 'PENDING'",
                now,
                eventId);
    }

    public int scheduleRetry(long eventId, LocalDateTime dueAt, String errorCode) {
        return jdbc.update(
                "UPDATE search_outbox SET due_at = ?, lease_until = NULL, last_error_code = ?"
                        + " WHERE event_id = ? AND status = 'PENDING'",
                dueAt,
                errorCode,
                eventId);
    }

    public int markFailed(long eventId, LocalDateTime now, String errorCode) {
        return jdbc.update(
                "UPDATE search_outbox SET status = 'FAILED', processed_at = ?, lease_until = NULL,"
                        + " last_error_code = ? WHERE event_id = ? AND status = 'PENDING'",
                now,
                errorCode,
                eventId);
    }

    public Map<Status, Long> countByStatus() {
        Map<Status, Long> counts = new EnumMap<>(Status.class);
        for (Status status : Status.values()) {
            counts.put(status, 0L);
        }
        jdbc.query(
                "SELECT status, COUNT(*) AS cnt FROM search_outbox GROUP BY status",
                rs -> {
                    counts.put(Status.valueOf(rs.getString("status")), rs.getLong("cnt"));
                });
        return Collections.unmodifiableMap(counts);
    }

    public LocalDateTime findOldestPendingCreatedAt() {
        return jdbc.queryForObject(
                "SELECT MIN(created_at) FROM search_outbox WHERE status = 'PENDING'",
                LocalDateTime.class);
    }

    /** 영구 실패 이벤트를 처음부터 다시 시도하게 되돌린다. last_error_code는 원인 확인용으로 남긴다. */
    public int retryFailed(LocalDateTime now) {
        return jdbc.update(
                "UPDATE search_outbox SET status = 'PENDING', attempts = 0, due_at = ?,"
                        + " lease_until = NULL, processed_at = NULL WHERE status = 'FAILED'",
                now);
    }

    /** 처리 끝난 이벤트를 보관 기간이 지나면 지운다. 한 번에 지우는 행 수를 제한해 긴 잠금을 피한다. */
    public int purgeDone(LocalDateTime before, int limit) {
        return jdbc.update(
                "DELETE FROM search_outbox WHERE status = 'DONE' AND processed_at < ?"
                        + " ORDER BY event_id LIMIT ?",
                before,
                limit);
    }

    public record ClaimedEvent(
            long eventId, SearchSourceType sourceType, long sourceId, long version, int attempt) {}
}
