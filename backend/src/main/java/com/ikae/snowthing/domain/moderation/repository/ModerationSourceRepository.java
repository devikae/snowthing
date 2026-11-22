package com.ikae.snowthing.domain.moderation.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.moderation.entity.ModerationTargetType;

/**
 * 검사할 원문을 처리 시점에 다시 읽는다. 대기열에는 본문이 없어서다. 엔티티의 삭제 필터를 거치지 않으려고 SQL로 읽고, 공개 여부는 여기서 판단한다.
 *
 * <ul>
 *   <li>글, 장터, 카풀: 지워지지 않았고 status = NORMAL. 금칙어 보류(FILTER_HOLD), 신고 임시조치, 블라인드는 이미 가려져 있어 검사하지
 *       않는다.
 *   <li>댓글: is_deleted = false (신고 임시조치, 관리자 블라인드, 금칙어 보류는 모두 삭제 상태를 함께 켠다). 댓글 읽기와 같이 부모 글도
 *       지워지지 않았고 status = NORMAL이어야 한다.
 *   <li>설질 제보: status = NORMAL.
 * </ul>
 */
@Repository
public class ModerationSourceRepository {

    private final JdbcTemplate jdbc;

    public ModerationSourceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Source> find(ModerationTargetType type, long targetId) {
        if (type.isPost()) {
            return jdbc
                    .query(
                            "SELECT p.title, p.content, p.status, p.is_deleted,"
                                    + " m.created_at AS member_created_at"
                                    + " FROM post p LEFT JOIN member m ON m.member_id = p.member_id"
                                    + " WHERE p.post_id = ?",
                            (rs, row) ->
                                    new Source(
                                            rs.getString("title"),
                                            rs.getString("content"),
                                            !rs.getBoolean("is_deleted")
                                                    && "NORMAL".equals(rs.getString("status")),
                                            rs.getObject("member_created_at", LocalDateTime.class)),
                            targetId)
                    .stream()
                    .findFirst();
        }
        if (type == ModerationTargetType.COMMENT) {
            return jdbc
                    .query(
                            "SELECT c.content, c.is_deleted, p.status AS post_status,"
                                    + " p.is_deleted AS post_deleted,"
                                    + " m.created_at AS member_created_at FROM comment c"
                                    + " JOIN post p ON p.post_id = c.post_id"
                                    + " LEFT JOIN member m ON m.member_id = c.member_id"
                                    + " WHERE c.comment_id = ?",
                            (rs, row) ->
                                    new Source(
                                            "",
                                            rs.getString("content"),
                                            !rs.getBoolean("is_deleted")
                                                    && !rs.getBoolean("post_deleted")
                                                    && "NORMAL".equals(rs.getString("post_status")),
                                            rs.getObject("member_created_at", LocalDateTime.class)),
                            targetId)
                    .stream()
                    .findFirst();
        }
        return jdbc
                .query(
                        "SELECT r.content, r.status, m.created_at AS member_created_at FROM"
                                + " resort_report r LEFT JOIN member m ON m.member_id = r.member_id"
                                + " WHERE r.report_id = ?",
                        (rs, row) ->
                                new Source(
                                        "",
                                        rs.getString("content"),
                                        "NORMAL".equals(rs.getString("status")),
                                        rs.getObject("member_created_at", LocalDateTime.class)),
                        targetId)
                .stream()
                .findFirst();
    }

    /**
     * @param visible 지금 다른 사람에게 보이는 상태인지. 아니면 검사하지 않는다
     * @param memberCreatedAt 작성 회원의 가입 시각 (비회원이면 null)
     */
    public record Source(
            String title, String body, boolean visible, LocalDateTime memberCreatedAt) {}
}
