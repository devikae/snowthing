package com.ikae.snowthing.domain.moderation.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.moderation.entity.ModerationReviewResult;
import com.ikae.snowthing.domain.moderation.entity.ModerationStage;
import com.ikae.snowthing.domain.moderation.entity.ModerationStatus;
import com.ikae.snowthing.domain.moderation.entity.ModerationTargetType;
import com.ikae.snowthing.domain.moderation.entity.ModerationVerdict;

/**
 * content_moderation 읽기와 쓰기. 본문은 다루지 않는다. 검색 색인 outbox({@code SearchOutboxRepository})와 같은 방식이다.
 */
@Repository
public class ContentModerationRepository {

    private static final String COLUMNS =
            "content_moderation_id, target_type, target_id, member_id, writer_ip, content_hash,"
                    + " status, stage, verdict, reasons, attempt_count, created_at, processed_at,"
                    + " review_result";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public ContentModerationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    /** 원문 변경과 같은 트랜잭션에서 부른다. */
    public void insert(
            ModerationTargetType type,
            long targetId,
            Long memberId,
            String writerIp,
            String contentHash,
            LocalDateTime now) {
        jdbc.update(
                "INSERT INTO content_moderation (target_type, target_id, member_id, writer_ip,"
                        + " content_hash, status, attempt_count, next_attempt_at, created_at,"
                        + " updated_at) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)",
                type.name(),
                targetId,
                memberId,
                writerIp,
                contentHash,
                now,
                now,
                now);
    }

    /**
     * 처리할 줄을 고르고 임대를 건다. 호출하는 쪽 트랜잭션 안에서 실행해야 한다.
     *
     * <p>FOR UPDATE SKIP LOCKED로 다른 처리기가 지금 고르는 줄은 건너뛴다. 고른 줄에 lease_until을 걸고 attempt_count를 미리 1
     * 올린다. 처리 도중 서버가 죽어도 시도 횟수가 남아 무한 재시도가 되지 않고, 임대가 끝나면 다시 가져간다.
     */
    public List<Row> claim(LocalDateTime now, LocalDateTime leaseUntil, int limit) {
        List<Row> rows =
                jdbc.query(
                        "SELECT "
                                + COLUMNS
                                + " FROM content_moderation"
                                + " WHERE status = 'PENDING' AND next_attempt_at <= ?"
                                + " AND (lease_until IS NULL OR lease_until <= ?)"
                                + " ORDER BY next_attempt_at, content_moderation_id LIMIT ?"
                                + " FOR UPDATE SKIP LOCKED",
                        (rs, rowNum) -> map(rs, 1),
                        now,
                        now,
                        limit);
        if (rows.isEmpty()) {
            return rows;
        }
        named.update(
                "UPDATE content_moderation SET lease_until = :leaseUntil,"
                        + " attempt_count = attempt_count + 1, updated_at = :now"
                        + " WHERE content_moderation_id IN (:ids)",
                Map.of(
                        "leaseUntil",
                        leaseUntil,
                        "now",
                        now,
                        "ids",
                        rows.stream().map(Row::id).toList()));
        return rows;
    }

    /** 판정 기록과 조치를 같은 트랜잭션에서 하려고 처리 중인 줄을 잠근다. 다른 처리기가 이미 끝냈으면 빈 값. */
    public Optional<Row> lockPending(long id) {
        return jdbc
                .query(
                        "SELECT "
                                + COLUMNS
                                + " FROM content_moderation WHERE content_moderation_id = ?"
                                + " AND status = 'PENDING' FOR UPDATE",
                        (rs, rowNum) -> map(rs, 0),
                        id)
                .stream()
                .findFirst();
    }

    /** 관리자 처리용. 판정이 끝났고 아직 검토하지 않은 HIDE, REVIEW 줄만 잠근다. */
    public Optional<Row> lockReviewable(long id) {
        return jdbc
                .query(
                        "SELECT "
                                + COLUMNS
                                + " FROM content_moderation WHERE content_moderation_id = ?"
                                + " AND verdict IN ('HIDE', 'REVIEW') AND review_result IS NULL"
                                + " AND status IN ('DONE', 'FAILED') FOR UPDATE",
                        (rs, rowNum) -> map(rs, 0),
                        id)
                .stream()
                .findFirst();
    }

    public int complete(
            long id,
            ModerationStatus status,
            ModerationStage stage,
            ModerationVerdict verdict,
            String reasons,
            LocalDateTime now) {
        return jdbc.update(
                "UPDATE content_moderation SET status = ?, stage = ?, verdict = ?, reasons = ?,"
                        + " processed_at = ?, lease_until = NULL, updated_at = ?"
                        + " WHERE content_moderation_id = ? AND status = 'PENDING'",
                status.name(),
                stage == null ? null : stage.name(),
                verdict == null ? null : verdict.name(),
                reasons,
                now,
                now,
                id);
    }

    public int scheduleRetry(long id, LocalDateTime nextAttemptAt, LocalDateTime now) {
        return jdbc.update(
                "UPDATE content_moderation SET next_attempt_at = ?, lease_until = NULL,"
                        + " updated_at = ? WHERE content_moderation_id = ? AND status = 'PENDING'",
                nextAttemptAt,
                now,
                id);
    }

    /** 같은 대상에 이 줄보다 새 줄(작성자가 다시 고침)이 있는지. */
    public boolean hasNewerRow(ModerationTargetType type, long targetId, long id) {
        Integer found =
                jdbc.query(
                        "SELECT 1 FROM content_moderation WHERE target_type = ? AND target_id = ?"
                                + " AND content_moderation_id > ? LIMIT 1",
                        rs -> rs.next() ? 1 : null,
                        type.name(),
                        targetId,
                        id);
        return found != null;
    }

    /** 같은 작성자 또는 같은 IP가 같은 내용을 쓴 다른 대상 수. 이 줄의 작성 시각 앞쪽 {@code since}부터 본다. 같은 대상의 수정본은 세지 않는다. */
    public int countDuplicateTargets(Row row, LocalDateTime since) {
        if (row.memberId() == null && row.writerIp() == null) {
            return 0;
        }
        Integer count =
                jdbc.queryForObject(
                        "SELECT COUNT(DISTINCT target_type, target_id) FROM content_moderation"
                                + " WHERE content_hash = ? AND created_at >= ? AND created_at <= ?"
                                + " AND NOT (target_type = ? AND target_id = ?)"
                                + " AND (member_id = ? OR writer_ip = ?)",
                        Integer.class,
                        row.contentHash(),
                        since,
                        row.createdAt(),
                        row.targetType().name(),
                        row.targetId(),
                        row.memberId(),
                        row.writerIp());
        return count == null ? 0 : count;
    }

    /** 관리자가 문제없음으로 본 내용인지. 같은 내용 해시는 다시 숨기지 않는다. */
    public boolean isClearedByAdmin(String contentHash) {
        Integer found =
                jdbc.query(
                        "SELECT 1 FROM content_moderation WHERE content_hash = ?"
                                + " AND review_result = 'PASS' LIMIT 1",
                        rs -> rs.next() ? 1 : null,
                        contentHash);
        return found != null;
    }

    /** 같은 대상의 이전 판정 중 아직 검토하지 않은 줄을 새 판정으로 대체됐다고 표시한다. */
    public int supersedeOlder(
            ModerationTargetType type, long targetId, long id, LocalDateTime now) {
        return jdbc.update(
                "UPDATE content_moderation SET review_result = 'SUPERSEDED', reviewed_at = ?,"
                        + " updated_at = ? WHERE target_type = ? AND target_id = ?"
                        + " AND content_moderation_id < ? AND verdict IN ('HIDE', 'REVIEW')"
                        + " AND review_result IS NULL",
                now,
                now,
                type.name(),
                targetId,
                id);
    }

    /**
     * 관리자 처리. 고른 줄과 같은 대상의 그보다 이전 미검토 판정을 함께 닫는다. 더 새 판정은 그 판정의 조치를 따로 받아야 하므로 닫지 않는다.
     */
    public int markReviewed(
            Row row, ModerationReviewResult result, Long reviewerId, LocalDateTime now) {
        return jdbc.update(
                "UPDATE content_moderation SET review_result = ?, reviewed_by = ?, reviewed_at = ?,"
                        + " updated_at = ? WHERE target_type = ? AND target_id = ?"
                        + " AND content_moderation_id <= ?"
                        + " AND verdict IN ('HIDE', 'REVIEW') AND review_result IS NULL"
                        + " AND status IN ('DONE', 'FAILED')",
                result.name(),
                reviewerId,
                now,
                now,
                row.targetType().name(),
                row.targetId(),
                row.id());
    }

    /** 검토 대기열. 최근 판정 순, ID 보조 정렬. 한 건 더 읽어 다음 쪽 여부를 안다. */
    public List<Row> findReviewQueue(int offset, int limit) {
        return jdbc.query(
                "SELECT "
                        + COLUMNS
                        + " FROM content_moderation WHERE verdict IN ('HIDE', 'REVIEW')"
                        + " AND review_result IS NULL AND status IN ('DONE', 'FAILED')"
                        + " ORDER BY content_moderation_id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> map(rs, 0),
                limit,
                offset);
    }

    /** 이 시간 동안 판정을 마친 줄의 자동 숨김과 검토 대기 수. */
    public VerdictCounts countVerdicts(LocalDateTime from, LocalDateTime to) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(CASE WHEN verdict = 'HIDE' THEN 1 ELSE 0 END), 0) AS hidden,"
                        + " COALESCE(SUM(CASE WHEN verdict = 'REVIEW' THEN 1 ELSE 0 END), 0)"
                        + " AS review"
                        + " FROM content_moderation WHERE processed_at >= ? AND processed_at < ?",
                (rs, rowNum) -> new VerdictCounts(rs.getLong("hidden"), rs.getLong("review")),
                from,
                to);
    }

    private static Row map(ResultSet rs, int attemptOffset) throws SQLException {
        String stage = rs.getString("stage");
        String verdict = rs.getString("verdict");
        String reviewResult = rs.getString("review_result");
        return new Row(
                rs.getLong("content_moderation_id"),
                ModerationTargetType.valueOf(rs.getString("target_type")),
                rs.getLong("target_id"),
                rs.getObject("member_id", Long.class),
                rs.getString("writer_ip"),
                rs.getString("content_hash"),
                ModerationStatus.valueOf(rs.getString("status")),
                stage == null ? null : ModerationStage.valueOf(stage),
                verdict == null ? null : ModerationVerdict.valueOf(verdict),
                rs.getString("reasons"),
                rs.getInt("attempt_count") + attemptOffset,
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("processed_at", LocalDateTime.class),
                reviewResult == null ? null : ModerationReviewResult.valueOf(reviewResult));
    }

    /**
     * @param attempt 지금 처리 중인 시도 번호(가져갈 때 올린 값 포함). 관리자 조회에서는 저장된 시도 횟수
     */
    public record Row(
            long id,
            ModerationTargetType targetType,
            long targetId,
            Long memberId,
            String writerIp,
            String contentHash,
            ModerationStatus status,
            ModerationStage stage,
            ModerationVerdict verdict,
            String reasons,
            int attempt,
            LocalDateTime createdAt,
            LocalDateTime processedAt,
            ModerationReviewResult reviewResult) {}

    public record VerdictCounts(long hidden, long review) {}
}
