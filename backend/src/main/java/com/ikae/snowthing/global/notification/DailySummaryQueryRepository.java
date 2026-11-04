package com.ikae.snowthing.global.notification;

import java.time.LocalDateTime;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 일일 요약 집계. 엔티티의 삭제 필터(@SQLRestriction)를 거치지 않도록 네이티브 SQL로 센다. 그날 작성됐다가 삭제된 글과 댓글도 작성 수에 포함한다.
 *
 * <p>AI 멤버는 {@code @snowthing.invalid} 이메일 계정이다. AI 기능을 꺼도 기존 AI 계정의 활동을 계속 구분할 수 있다.
 */
@Repository
@RequiredArgsConstructor
public class DailySummaryQueryRepository {

    static final String AI_EMAIL_PATTERN = "%@snowthing.invalid";

    private final NamedParameterJdbcTemplate jdbc;

    public long countHumanSignUps(LocalDateTime from, LocalDateTime to) {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM member WHERE created_at >= :from AND created_at < :to"
                                + " AND email NOT LIKE :ai",
                        params(from, to),
                        Long.class);
        return count != null ? count : 0;
    }

    public Split countPosts(LocalDateTime from, LocalDateTime to) {
        return countByWriter("post", from, to);
    }

    public Split countComments(LocalDateTime from, LocalDateTime to) {
        return countByWriter("comment", from, to);
    }

    private Split countByWriter(String table, LocalDateTime from, LocalDateTime to) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) AS total,"
                        + " COALESCE(SUM(CASE WHEN m.email LIKE :ai THEN 1 ELSE 0 END), 0) AS ai"
                        + " FROM "
                        + table
                        + " t LEFT JOIN member m ON m.member_id = t.member_id"
                        + " WHERE t.created_at >= :from AND t.created_at < :to",
                params(from, to),
                (rs, row) -> {
                    long ai = rs.getLong("ai");
                    return new Split(rs.getLong("total") - ai, ai);
                });
    }

    private static MapSqlParameterSource params(LocalDateTime from, LocalDateTime to) {
        return new MapSqlParameterSource("from", from)
                .addValue("to", to)
                .addValue("ai", AI_EMAIL_PATTERN);
    }

    public record Split(long human, long ai) {

        public long total() {
            return human + ai;
        }
    }
}
