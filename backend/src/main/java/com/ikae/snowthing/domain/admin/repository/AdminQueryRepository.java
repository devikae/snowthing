package com.ikae.snowthing.domain.admin.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.admin.dto.AdminActionLogItem;
import com.ikae.snowthing.domain.admin.dto.AdminCommentItem;
import com.ikae.snowthing.domain.admin.dto.AdminInquiryItem;
import com.ikae.snowthing.domain.admin.dto.AdminMemberDetailResponse.RecentComment;
import com.ikae.snowthing.domain.admin.dto.AdminMemberDetailResponse.RecentPost;
import com.ikae.snowthing.domain.admin.dto.AdminResortReportItem;
import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 화면 조회 전용 쿼리 (IKA-78). 삭제된 글과 댓글도 봐야 해서 {@code @SQLRestriction}이 붙지 않는 SQL을 쓴다.
 *
 * <ul>
 *   <li>목록은 size + 1건을 읽어 다음 쪽 여부만 판단한다. 전체 건수를 세지 않는다.
 *   <li>정렬은 모두 ID 역순이거나 ID를 보조 정렬로 붙인다.
 *   <li>일별 집계는 050의 created_at 인덱스로 기간 범위만 읽는다.
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class AdminQueryRepository {

    public static final int PREVIEW_LENGTH = 200;

    private final NamedParameterJdbcTemplate jdbc;

    /** 테이블별 KST 날짜 단위 건수. 키는 날짜, 값은 건수다. 표 이름은 이 클래스 안의 상수만 받는다. */
    public Map<LocalDate, Long> countDaily(String table, LocalDateTime from) {
        if (!List.of("member", "post", "comment").contains(table)) {
            throw new IllegalArgumentException(table);
        }
        Map<LocalDate, Long> counts = new HashMap<>();
        jdbc.query(
                "SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM "
                        + table
                        + " WHERE created_at >= :from GROUP BY DATE(created_at)",
                new MapSqlParameterSource("from", from),
                rs -> {
                    counts.put(rs.getDate("day").toLocalDate(), rs.getLong("cnt"));
                });
        return counts;
    }

    public long countPendingReports() {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM content_report WHERE status = 'PENDING'",
                        new MapSqlParameterSource(),
                        Long.class);
        return count == null ? 0 : count;
    }

    public long countUnansweredInquiries() {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM post p"
                                + " JOIN post_category pc ON pc.category_id = p.category_id"
                                + " WHERE pc.code = :inquiry AND p.is_deleted = false"
                                + " AND NOT EXISTS ("
                                + ADMIN_ANSWER
                                + ")",
                        new MapSqlParameterSource("inquiry", PostCategoryCodes.INQUIRY),
                        Long.class);
        return count == null ? 0 : count;
    }

    private static final String ADMIN_ANSWER =
            "SELECT 1 FROM comment ac JOIN member am ON am.member_id = ac.member_id"
                    + " WHERE ac.post_id = p.post_id AND ac.is_deleted = false"
                    + " AND am.role = 'ROLE_ADMIN'";

    public List<AdminInquiryItem> findInquiries(String status, int offset, int limit) {
        String answered = "EXISTS (" + ADMIN_ANSWER + ")";
        String condition =
                switch (status) {
                    case "ANSWERED" -> " AND " + answered;
                    case "UNANSWERED" -> " AND NOT " + answered;
                    default -> "";
                };
        return jdbc.query(
                "SELECT p.public_id, p.title, m.nickname, p.comment_count, p.created_at, "
                        + answered
                        + " AS answered FROM post p"
                        + " JOIN post_category pc ON pc.category_id = p.category_id"
                        + " LEFT JOIN member m ON m.member_id = p.member_id"
                        + " WHERE pc.code = :inquiry AND p.is_deleted = false"
                        + condition
                        + " ORDER BY p.post_id DESC LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource("inquiry", PostCategoryCodes.INQUIRY)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                (rs, row) ->
                        new AdminInquiryItem(
                                rs.getString("public_id"),
                                rs.getString("title"),
                                rs.getString("nickname"),
                                rs.getBoolean("answered"),
                                rs.getInt("comment_count"),
                                time(rs, "created_at")));
    }

    /**
     * 관리자 댓글 검색. filter는 ALL, VISIBLE, HELD(블라인드나 임시조치), DELETED. 내용 검색은 앞에 %가 붙어 인덱스를 못 타지만 ID
     * 역순으로 페이지 크기만큼 읽고 멈춘다.
     */
    public List<AdminCommentItem> findComments(
            String filter, String contentPattern, String authorPattern, int offset, int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        switch (filter) {
            case "VISIBLE" -> where.append(" AND c.is_deleted = false");
            case "HELD" -> where.append(" AND c.moderation_held = true");
            case "DELETED" ->
                    where.append(" AND c.is_deleted = true AND c.moderation_held = false");
            default -> {}
        }
        MapSqlParameterSource params =
                new MapSqlParameterSource("limit", limit).addValue("offset", offset);
        if (contentPattern != null) {
            where.append(" AND c.content LIKE :content ESCAPE '!'");
            params.addValue("content", contentPattern);
        }
        if (authorPattern != null) {
            where.append(" AND m.nickname LIKE :author ESCAPE '!'");
            params.addValue("author", authorPattern);
        }
        return jdbc.query(
                "SELECT c.comment_id, c.content, c.is_anonymous, c.is_deleted, c.moderation_held,"
                        + " c.created_at, m.nickname, m.public_id AS member_public_id,"
                        + " p.public_id AS post_public_id, p.title, pc.code"
                        + " FROM comment c"
                        + " JOIN post p ON p.post_id = c.post_id"
                        + " JOIN post_category pc ON pc.category_id = p.category_id"
                        + " LEFT JOIN member m ON m.member_id = c.member_id"
                        + where
                        + " ORDER BY c.comment_id DESC LIMIT :limit OFFSET :offset",
                params,
                (rs, row) ->
                        new AdminCommentItem(
                                rs.getLong("comment_id"),
                                preview(rs.getString("content")),
                                rs.getString("nickname"),
                                rs.getString("member_public_id"),
                                rs.getBoolean("is_anonymous"),
                                rs.getBoolean("is_deleted"),
                                rs.getBoolean("moderation_held"),
                                rs.getString("post_public_id"),
                                rs.getString("title"),
                                rs.getString("code"),
                                time(rs, "created_at")));
    }

    /** status가 null이면 전체. 상태별 조회는 (status, created_at, report_id) 인덱스를 탄다. */
    public List<AdminResortReportItem> findResortReports(String status, int offset, int limit) {
        MapSqlParameterSource params =
                new MapSqlParameterSource("limit", limit).addValue("offset", offset);
        String where = "";
        if (status != null) {
            where = " WHERE r.status = :status";
            params.addValue("status", status);
        }
        return jdbc.query(
                "SELECT r.report_id, rs.name AS resort_name, r.content, m.nickname, r.status,"
                        + " r.created_at FROM resort_report r"
                        + " JOIN resort rs ON rs.resort_id = r.resort_id"
                        + " LEFT JOIN member m ON m.member_id = r.member_id"
                        + where
                        + " ORDER BY r.created_at DESC, r.report_id DESC"
                        + " LIMIT :limit OFFSET :offset",
                params,
                (rs, row) ->
                        new AdminResortReportItem(
                                rs.getLong("report_id"),
                                rs.getString("resort_name"),
                                rs.getString("content"),
                                rs.getString("nickname"),
                                rs.getString("status"),
                                time(rs, "created_at")));
    }

    public List<AdminActionLogItem> findActionLogs(
            String action,
            String targetType,
            String targetId,
            Long actorMemberId,
            int offset,
            int limit) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource params =
                new MapSqlParameterSource("limit", limit).addValue("offset", offset);
        if (action != null) {
            where.append(" AND l.action = :action");
            params.addValue("action", action);
        }
        if (targetType != null) {
            where.append(" AND l.target_type = :targetType");
            params.addValue("targetType", targetType);
        }
        if (targetId != null) {
            where.append(" AND l.target_id = :targetId");
            params.addValue("targetId", targetId);
        }
        if (actorMemberId != null) {
            where.append(" AND l.actor_member_id = :actor");
            params.addValue("actor", actorMemberId);
        }
        return jdbc.query(
                "SELECT l.admin_action_log_id, l.action, l.target_type, l.target_id, l.reason,"
                        + " l.created_at, m.nickname, m.public_id FROM admin_action_log l"
                        + " LEFT JOIN member m ON m.member_id = l.actor_member_id"
                        + where
                        + " ORDER BY l.admin_action_log_id DESC LIMIT :limit OFFSET :offset",
                params,
                (rs, row) ->
                        new AdminActionLogItem(
                                rs.getLong("admin_action_log_id"),
                                rs.getString("action"),
                                rs.getString("target_type"),
                                rs.getString("target_id"),
                                rs.getString("reason"),
                                rs.getString("nickname"),
                                rs.getString("public_id"),
                                time(rs, "created_at")));
    }

    public Optional<Long> findMemberIdByPublicId(String publicId) {
        return jdbc
                .query(
                        "SELECT member_id FROM member WHERE public_id = :publicId",
                        new MapSqlParameterSource("publicId", publicId),
                        (rs, row) -> rs.getLong("member_id"))
                .stream()
                .findFirst();
    }

    public List<String> findSocialProviders(Long memberId) {
        return jdbc.queryForList(
                "SELECT provider FROM member_social_account WHERE member_id = :memberId"
                        + " ORDER BY provider",
                new MapSqlParameterSource("memberId", memberId),
                String.class);
    }

    public long countPostsByMember(Long memberId) {
        return count("SELECT COUNT(*) FROM post WHERE member_id = :memberId", memberId);
    }

    public long countCommentsByMember(Long memberId) {
        return count("SELECT COUNT(*) FROM comment WHERE member_id = :memberId", memberId);
    }

    /** 작성자 FK 인덱스(member_id)를 타고, 보조 키인 ID 역순으로 읽는다. */
    public List<RecentPost> findRecentPosts(Long memberId, int limit) {
        return jdbc.query(
                "SELECT p.public_id, p.title, pc.code, pc.name, p.status, p.is_deleted,"
                        + " p.created_at FROM post p"
                        + " JOIN post_category pc ON pc.category_id = p.category_id"
                        + " WHERE p.member_id = :memberId ORDER BY p.post_id DESC LIMIT :limit",
                new MapSqlParameterSource("memberId", memberId).addValue("limit", limit),
                (rs, row) ->
                        new RecentPost(
                                rs.getString("public_id"),
                                rs.getString("title"),
                                rs.getString("code"),
                                rs.getString("name"),
                                rs.getString("status"),
                                rs.getBoolean("is_deleted"),
                                time(rs, "created_at")));
    }

    public List<RecentComment> findRecentComments(Long memberId, int limit) {
        return jdbc.query(
                "SELECT c.comment_id, c.content, c.is_deleted, c.moderation_held, c.created_at,"
                        + " p.public_id, p.title, pc.code FROM comment c"
                        + " JOIN post p ON p.post_id = c.post_id"
                        + " JOIN post_category pc ON pc.category_id = p.category_id"
                        + " WHERE c.member_id = :memberId ORDER BY c.comment_id DESC LIMIT :limit",
                new MapSqlParameterSource("memberId", memberId).addValue("limit", limit),
                (rs, row) ->
                        new RecentComment(
                                rs.getLong("comment_id"),
                                preview(rs.getString("content")),
                                rs.getString("public_id"),
                                rs.getString("title"),
                                rs.getString("code"),
                                rs.getBoolean("is_deleted"),
                                rs.getBoolean("moderation_held"),
                                time(rs, "created_at")));
    }

    private long count(String sql, Long memberId) {
        Long value =
                jdbc.queryForObject(
                        sql, new MapSqlParameterSource("memberId", memberId), Long.class);
        return value == null ? 0 : value;
    }

    private static String preview(String content) {
        if (content == null || content.length() <= PREVIEW_LENGTH) {
            return content;
        }
        return content.substring(0, PREVIEW_LENGTH) + "...";
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }
}
