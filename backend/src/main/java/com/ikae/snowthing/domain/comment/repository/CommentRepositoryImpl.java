package com.ikae.snowthing.domain.comment.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    public Optional<CursorPosition> findRootCursor(Long postId, Long cursorId) {
        return findCursor(
                """
                SELECT created_at, comment_id
                FROM comment
                WHERE post_id = :scopeId AND parent_id IS NULL AND comment_id = :cursorId
                """,
                postId,
                cursorId);
    }

    @Override
    public Optional<CursorPosition> findReplyCursor(Long rootCommentId, Long cursorId) {
        return findCursor(
                """
                SELECT created_at, comment_id
                FROM comment
                WHERE parent_id = :scopeId AND comment_id = :cursorId
                """,
                rootCommentId,
                cursorId);
    }

    @Override
    public List<CommentResponse> findRootComments(
            Long postId, CursorPosition cursor, int fetchSize) {
        String cursorCondition =
                cursor == null
                        ? ""
                        : """
                         AND (c.created_at > :cursorCreatedAt
                              OR (c.created_at = :cursorCreatedAt AND c.comment_id > :cursorId))
                        """;
        String sql =
                "SELECT "
                        + SELECT_RESPONSE_COLUMNS
                        + """
                        , (SELECT COUNT(*) FROM comment active_reply
                           WHERE active_reply.parent_id = c.comment_id
                             AND active_reply.is_deleted = false) AS reply_count,
                          CASE WHEN (SELECT COUNT(*) FROM comment all_reply
                                      WHERE all_reply.parent_id = c.comment_id) > 5
                               THEN true ELSE false END AS has_more_replies
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
                        + " ORDER BY c.created_at ASC, c.comment_id ASC LIMIT :fetchSize";

        MapSqlParameterSource params =
                new MapSqlParameterSource("postId", postId).addValue("fetchSize", fetchSize);
        addCursorParameters(params, cursor);
        return jdbcTemplate.query(sql, params, this::mapResponse);
    }

    @Override
    public Map<Long, List<CommentResponse>> findTopReplyPreviews(List<Long> rootCommentIds) {
        if (rootCommentIds.isEmpty()) {
            return Map.of();
        }
        String sql =
                """
                SELECT ranked.*
                FROM (
                    SELECT c.comment_id, c.post_id, c.parent_id, c.content, c.is_deleted,
                           c.is_anonymous, c.writer_ip, c.created_at,
                           m.public_id AS member_public_id, m.nickname, m.profile_image_url,
                           0 AS reply_count, false AS has_more_replies,
                           ROW_NUMBER() OVER (
                               PARTITION BY c.parent_id
                               ORDER BY c.created_at ASC, c.comment_id ASC
                           ) AS rn
                    FROM comment c
                    LEFT JOIN member m ON m.member_id = c.member_id
                    WHERE c.parent_id IN (:rootCommentIds)
                ) ranked
                WHERE ranked.rn <= 5
                ORDER BY ranked.parent_id ASC, ranked.created_at ASC, ranked.comment_id ASC
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
    public List<CommentResponse> findReplies(
            Long rootCommentId, CursorPosition cursor, int fetchSize) {
        String cursorCondition =
                cursor == null
                        ? ""
                        : """
                         AND (c.created_at > :cursorCreatedAt
                              OR (c.created_at = :cursorCreatedAt AND c.comment_id > :cursorId))
                        """;
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
                        + " ORDER BY c.created_at ASC, c.comment_id ASC LIMIT :fetchSize";
        MapSqlParameterSource params =
                new MapSqlParameterSource("rootCommentId", rootCommentId)
                        .addValue("fetchSize", fetchSize);
        addCursorParameters(params, cursor);
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

    private Optional<CursorPosition> findCursor(String sql, Long scopeId, Long cursorId) {
        List<CursorPosition> positions =
                jdbcTemplate.query(
                        sql,
                        new MapSqlParameterSource("scopeId", scopeId)
                                .addValue("cursorId", cursorId),
                        (rs, rowNum) ->
                                new CursorPosition(
                                        rs.getObject("created_at", LocalDateTime.class),
                                        rs.getLong("comment_id")));
        return positions.stream().findFirst();
    }

    private void addCursorParameters(MapSqlParameterSource params, CursorPosition cursorPosition) {
        if (cursorPosition != null) {
            params.addValue("cursorCreatedAt", cursorPosition.createdAt());
            params.addValue("cursorId", cursorPosition.commentId());
        }
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
