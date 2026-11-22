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
import com.ikae.snowthing.domain.contentfilter.service.ContentFilterService.CommentViewer;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CommentRepositoryImpl implements CommentRepositoryCustom {

    private static final String SELECT_RESPONSE_COLUMNS =
            """
            c.comment_id, c.post_id, c.parent_id, c.content, c.is_deleted, c.moderation_held,
            c.filter_state, c.is_anonymous, c.writer_ip, c.created_at,
            m.public_id AS member_public_id, m.nickname, m.profile_image_url, m.status AS member_status
            """;

    /**
     * 금칙어 보류 댓글은 작성자 본인에게만 남긴다 (IKA-96). 회원은 회원 ID, 비회원은 같은 IP이고 보류 뒤 짧은 시간 안이다. 비회원 확인 시각은 보류하거나
     * 고친 시각인 updated_at으로 본다. 별칭 c를 쓴다.
     */
    private static final String FILTER_VISIBLE =
            """
             AND (c.filter_state IS NULL
                  OR (c.filter_state = 'HELD'
                      AND (c.member_id = :viewerMemberId
                           OR (c.member_id IS NULL AND c.writer_ip = :viewerIp
                               AND c.updated_at >= :anonymousSince))))
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
    public List<CommentResponse> findRootComments(
            Long postId, Long cursorId, int fetchSize, CommentViewer viewer) {
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
                        """
                        + FILTER_VISIBLE
                        + cursorCondition
                        + " ORDER BY c.comment_id ASC LIMIT :fetchSize";

        MapSqlParameterSource params =
                viewerParams(viewer).addValue("postId", postId).addValue("fetchSize", fetchSize);
        if (cursorId != null) {
            params.addValue("cursorId", cursorId);
        }
        return jdbcTemplate.query(sql, params, this::mapResponse);
    }

    @Override
    public Map<Long, ReplyStats> findReplyStats(List<Long> rootCommentIds, CommentViewer viewer) {
        if (rootCommentIds.isEmpty()) {
            return Map.of();
        }
        String sql =
                """
                SELECT c.parent_id,
                       COUNT(CASE WHEN c.is_deleted = false OR c.filter_state = 'HELD' THEN 1 END)
                           AS active_count,
                       COUNT(*) AS total_count
                FROM comment c
                WHERE c.parent_id IN (:rootCommentIds)
                """
                        + FILTER_VISIBLE
                        + " GROUP BY c.parent_id";

        Map<Long, ReplyStats> stats = new LinkedHashMap<>();
        jdbcTemplate.query(
                sql,
                viewerParams(viewer).addValue("rootCommentIds", rootCommentIds),
                rs -> {
                    long parentId = rs.getLong("parent_id");
                    long activeCount = rs.getLong("active_count");
                    long totalCount = rs.getLong("total_count");
                    stats.put(parentId, new ReplyStats(activeCount, totalCount));
                });
        return Map.copyOf(stats);
    }

    @Override
    public Map<Long, List<CommentResponse>> findTopReplyPreviews(
            List<Long> rootCommentIds, CommentViewer viewer) {
        if (rootCommentIds.isEmpty()) {
            return Map.of();
        }
        String sql =
                """
                SELECT r.comment_id, r.post_id, r.parent_id, r.content, r.is_deleted, r.moderation_held,
                       r.filter_state, r.is_anonymous, r.writer_ip, r.created_at,
                       m.public_id AS member_public_id, m.nickname, m.profile_image_url, m.status AS member_status,
                       0 AS reply_count, false AS has_more_replies
                FROM comment root
                CROSS JOIN LATERAL (
                    SELECT c.comment_id, c.post_id, c.parent_id, c.content, c.is_deleted, c.moderation_held,
                           c.filter_state, c.is_anonymous, c.writer_ip, c.created_at, c.member_id
                    FROM comment c
                    WHERE c.parent_id = root.comment_id
                """
                        + FILTER_VISIBLE
                        + """
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
                        viewerParams(viewer).addValue("rootCommentIds", rootCommentIds),
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
            Long rootCommentId, Long cursorId, int fetchSize, CommentViewer viewer) {
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
                        + FILTER_VISIBLE
                        + cursorCondition
                        + " ORDER BY c.comment_id ASC LIMIT :fetchSize";
        MapSqlParameterSource params =
                viewerParams(viewer)
                        .addValue("rootCommentId", rootCommentId)
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
    public long countReplies(Long rootCommentId, CommentViewer viewer) {
        Long count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*) FROM comment c
                        WHERE c.parent_id = :rootCommentId
                        """
                                + FILTER_VISIBLE,
                        viewerParams(viewer).addValue("rootCommentId", rootCommentId),
                        Long.class);
        return count == null ? 0 : count;
    }

    private CommentResponse mapResponse(ResultSet rs, int rowNum) throws SQLException {
        boolean anonymous = rs.getBoolean("is_anonymous");
        // 금칙어 보류 행은 작성자 본인에게만 오므로 평소 댓글처럼 보낸다.
        boolean deleted =
                rs.getBoolean("is_deleted") && !"HELD".equals(rs.getString("filter_state"));
        boolean held = rs.getBoolean("moderation_held");
        String memberPublicId = rs.getString("member_public_id");
        WriterResponse writer =
                !anonymous
                                && memberPublicId != null
                                && !MemberStatus.WITHDRAWN
                                        .name()
                                        .equals(rs.getString("member_status"))
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
                anonymous ? WriterDisplayFormatter.maskIp(rs.getString("writer_ip")) : null,
                held
                        ? CommentResponse.HELD_TEXT
                        : deleted ? CommentResponse.DELETED_TEXT : rs.getString("content"),
                deleted,
                rs.getLong("reply_count"),
                List.of(),
                rs.getBoolean("has_more_replies"),
                memberPublicId,
                false,
                false,
                false,
                false,
                rs.getObject("created_at", LocalDateTime.class),
                held,
                null);
    }

    /** 보는 사람을 모르면 아무 보류 댓글과도 맞지 않는 값(null)이 들어간다. */
    private static MapSqlParameterSource viewerParams(CommentViewer viewer) {
        return new MapSqlParameterSource()
                .addValue("viewerMemberId", viewer == null ? null : viewer.memberId())
                .addValue("viewerIp", viewer == null ? null : viewer.ip())
                .addValue(
                        "anonymousSince",
                        viewer == null || viewer.anonymousSince() == null
                                ? LocalDateTime.now()
                                : viewer.anonymousSince());
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
