package com.ikae.snowthing.domain.comment.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.comment.dto.CommentResponse;
import com.ikae.snowthing.domain.comment.dto.CommentResponse.WriterResponse;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepositoryCustom {

    private static final String SELECT_RESPONSE_COLUMNS =
            """
            c.comment_id, c.post_id, c.parent_id, c.content, c.is_deleted,
            c.is_anonymous, c.writer_ip, c.created_at,
            m.public_id AS member_public_id, m.nickname, m.profile_image_url
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public boolean existsRootCursor(Long postId, Long cursorId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM comment
                        WHERE post_id = :postId AND parent_id IS NULL AND comment_id = :cursorId
                        """,
                        new MapSqlParameterSource("postId", postId).addValue("cursorId", cursorId),
                        Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsReplyCursor(Long rootCommentId, Long cursorId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM comment
                        WHERE parent_id = :rootCommentId AND comment_id = :cursorId
                        """,
                        new MapSqlParameterSource("rootCommentId", rootCommentId)
                                .addValue("cursorId", cursorId),
                        Integer.class);
        return count != null && count > 0;
    }

    @Override
    public List<CommentResponse> findRootComments(Long postId, Long cursorId, int fetchSize) {
        String cursorCondition = (cursorId != null) ? " AND c.comment_id > :cursorId" : "";
        String sql =
                "SELECT "
                        + SELECT_RESPONSE_COLUMNS
                        + """
                        , 0 AS reply_count, false AS has_more_replies
                        FROM comment c
                        LEFT JOIN member m ON m.member_id = c.member_id
                        WHERE c.post_id = :postId
                          AND c.parent_id IS NULL
                          AND (c.is_deleted = false OR EXISTS (
                              SELECT 1 FROM comment active_child
                              WHERE active_child.parent_id = c.comment_id
                                AND active_child.is_deleted = false))
                        """
                        + cursorCondition
                        + " ORDER BY c.comment_id ASC LIMIT :fetchSize";

        MapSqlParameterSource params =
                new MapSqlParameterSource("postId", postId).addValue("fetchSize", fetchSize);
        if (cursorId != null) {
            params.addValue("cursorId", cursorId);
        }
        return jdbcTemplate.query(sql, params, this::mapResponse);
    }

    @Override
    public Map<Long, ReplyStats> findReplyStats(List<Long> rootCommentIds) {
        if (rootCommentIds.isEmpty()) {
            return Map.of();
        }
        String sql =
                """
                SELECT parent_id,
                       COUNT(CASE WHEN is_deleted = false THEN 1 END) AS active_count,
                       COUNT(*) AS total_count
                FROM comment
                WHERE parent_id IN (:rootCommentIds)
                GROUP BY parent_id
                """;

        Map<Long, ReplyStats> stats = new LinkedHashMap<>();
        jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("rootCommentIds", rootCommentIds),
                rs -> {
                    long parentId = rs.getLong("parent_id");
                    long activeCount = rs.getLong("active_count");
                    long totalCount = rs.getLong("total_count");
                    stats.put(parentId, new ReplyStats(activeCount, totalCount));
                });
        return Map.copyOf(stats);
    }

    @Override
    public Map<Long, List<CommentResponse>> findTopReplyPreviews(List<Long> rootCommentIds) {
        if (rootCommentIds.isEmpty()) {
            return Map.of();
        }
        String sql =
                """
                SELECT r.comment_id, r.post_id, r.parent_id, r.content, r.is_deleted,
                       r.is_anonymous, r.writer_ip, r.created_at,
                       m.public_id AS member_public_id, m.nickname, m.profile_image_url,
                       0 AS reply_count, false AS has_more_replies
                FROM comment root
                CROSS JOIN LATERAL (
                    SELECT c.comment_id, c.post_id, c.parent_id, c.content, c.is_deleted,
                           c.is_anonymous, c.writer_ip, c.created_at, c.member_id
                    FROM comment c
                    WHERE c.parent_id = root.comment_id
                    ORDER BY c.comment_id ASC
                    LIMIT 5
                ) r
                LEFT JOIN member m ON m.member_id = r.member_id
                WHERE root.comment_id IN (:rootCommentIds)
                ORDER BY root.comment_id ASC, r.comment_id ASC
                """;

        List<CommentResponse> replies =
                jdbcTemplate.query(
                        sql,
                        new MapSqlParameterSource("rootCommentIds", rootCommentIds),
                        this::mapResponse);
        Map<Long, List<CommentResponse>> grouped = new LinkedHashMap<>();
        for (CommentResponse reply : replies) {
            grouped.computeIfAbsent(reply.parentId(), ignored -> new java.util.ArrayList<>())
                    .add(reply);
        }
        grouped.replaceAll((ignored, values) -> List.copyOf(values));
        return Map.copyOf(grouped);
    }

    @Override
    public List<CommentResponse> findReplies(Long rootCommentId, Long cursorId, int fetchSize) {
        String cursorCondition = (cursorId != null) ? " AND c.comment_id > :cursorId" : "";
        String sql =
                "SELECT "
                        + SELECT_RESPONSE_COLUMNS
                        + """
                        , 0 AS reply_count, false AS has_more_replies
                        FROM comment c
                        LEFT JOIN member m ON m.member_id = c.member_id
                        WHERE c.parent_id = :rootCommentId
                        """
                        + cursorCondition
                        + " ORDER BY c.comment_id ASC LIMIT :fetchSize";
        MapSqlParameterSource params =
                new MapSqlParameterSource("rootCommentId", rootCommentId)
                        .addValue("fetchSize", fetchSize);
        if (cursorId != null) {
            params.addValue("cursorId", cursorId);
        }
        return jdbcTemplate.query(sql, params, this::mapResponse);
    }

    @Override
    public long countActiveReplies(Long rootCommentId) {
        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM comment
                        WHERE parent_id = :rootCommentId AND is_deleted = false
                        """,
                        new MapSqlParameterSource("rootCommentId", rootCommentId),
                        Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public long countReplies(Long rootCommentId) {
        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM comment
                        WHERE parent_id = :rootCommentId
                        """,
                        new MapSqlParameterSource("rootCommentId", rootCommentId),
                        Long.class);
        return count == null ? 0 : count;
    }

    private CommentResponse mapResponse(ResultSet rs, int rowNum) throws SQLException {
        boolean anonymous = rs.getBoolean("is_anonymous");
        boolean deleted = rs.getBoolean("is_deleted");
        String memberPublicId = rs.getString("member_public_id");
        WriterResponse writer =
                !anonymous && memberPublicId != null
                        ? new WriterResponse(
                                memberPublicId,
                                rs.getString("nickname"),
                                rs.getString("profile_image_url"))
                        : null;
        return new CommentResponse(
                rs.getLong("comment_id"),
                rs.getLong("post_id"),
                nullableLong(rs, "parent_id"),
                writer,
                anonymous,
                WriterDisplayFormatter.maskIp(rs.getString("writer_ip")),
                deleted ? "삭제된 댓글입니다." : rs.getString("content"),
                deleted,
                rs.getLong("reply_count"),
                List.of(),
                rs.getBoolean("has_more_replies"),
                rs.getObject("created_at", LocalDateTime.class));
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
