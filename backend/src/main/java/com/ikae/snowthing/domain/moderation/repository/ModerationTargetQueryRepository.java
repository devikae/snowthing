package com.ikae.snowthing.domain.moderation.repository;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.moderation.entity.ModerationTargetType;

/**
 * 관리자 검토 목록에 붙일 원문 정보. 한 쪽(최대 50줄)만 읽으므로 줄마다 한 번씩 묻는다. 본문은 DB에서 앞부분만 잘라 오고 태그를 지워 짧게 준다. 엔티티의 삭제
 * 필터를 거치지 않으려고 SQL로 읽는다(지워진 대상도 "삭제됨"으로 보여 준다).
 */
@Repository
public class ModerationTargetQueryRepository {

    private static final int SOURCE_LENGTH = 1000;
    private static final int EXCERPT_LENGTH = 200;
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final JdbcTemplate jdbc;

    public ModerationTargetQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TargetSummary> find(ModerationTargetType type, long targetId) {
        if (type.isPost()) {
            return jdbc
                    .query(
                            "SELECT p.public_id, pc.code, p.title, LEFT(p.content, ?) AS body,"
                                    + " p.status, p.is_deleted, m.nickname"
                                    + " FROM post p JOIN post_category pc ON pc.category_id ="
                                    + " p.category_id LEFT JOIN member m ON m.member_id ="
                                    + " p.member_id WHERE p.post_id = ?",
                            (rs, row) ->
                                    new TargetSummary(
                                            rs.getString("public_id"),
                                            rs.getString("code"),
                                            rs.getString("title"),
                                            excerpt(rs.getString("body")),
                                            postState(
                                                    rs.getBoolean("is_deleted"),
                                                    rs.getString("status")),
                                            rs.getString("nickname")),
                            SOURCE_LENGTH,
                            targetId)
                    .stream()
                    .findFirst();
        }
        if (type == ModerationTargetType.COMMENT) {
            return jdbc
                    .query(
                            "SELECT p.public_id, pc.code, p.title, c.content, c.is_deleted,"
                                    + " c.moderation_held, m.nickname FROM comment c"
                                    + " JOIN post p ON p.post_id = c.post_id"
                                    + " JOIN post_category pc ON pc.category_id = p.category_id"
                                    + " LEFT JOIN member m ON m.member_id = c.member_id"
                                    + " WHERE c.comment_id = ?",
                            (rs, row) ->
                                    new TargetSummary(
                                            rs.getString("public_id"),
                                            rs.getString("code"),
                                            rs.getString("title"),
                                            excerpt(rs.getString("content")),
                                            rs.getBoolean("moderation_held")
                                                    ? TargetState.HIDDEN
                                                    : rs.getBoolean("is_deleted")
                                                            ? TargetState.DELETED
                                                            : TargetState.VISIBLE,
                                            rs.getString("nickname")),
                            targetId)
                    .stream()
                    .findFirst();
        }
        return jdbc
                .query(
                        "SELECT r.content, r.status, m.nickname FROM resort_report r"
                                + " LEFT JOIN member m ON m.member_id = r.member_id"
                                + " WHERE r.report_id = ?",
                        (rs, row) ->
                                new TargetSummary(
                                        null,
                                        null,
                                        null,
                                        excerpt(rs.getString("content")),
                                        postState(
                                                "DELETED".equals(rs.getString("status")),
                                                rs.getString("status")),
                                        rs.getString("nickname")),
                        targetId)
                .stream()
                .findFirst();
    }

    private static TargetState postState(boolean deleted, String status) {
        if (deleted) return TargetState.DELETED;
        return "NORMAL".equals(status) ? TargetState.VISIBLE : TargetState.HIDDEN;
    }

    static String excerpt(String body) {
        if (body == null) return "";
        String text = SPACES.matcher(TAG.matcher(body).replaceAll(" ")).replaceAll(" ").strip();
        return text.length() > EXCERPT_LENGTH ? text.substring(0, EXCERPT_LENGTH) + "…" : text;
    }

    public enum TargetState {
        VISIBLE,
        HIDDEN,
        DELETED
    }

    /**
     * @param postPublicId 글이면 그 글, 댓글이면 댓글이 달린 글 (설질 제보는 null)
     * @param categoryCode 글 게시판 코드 (원문 링크를 만들 때 쓴다)
     * @param title 글 제목 (댓글이면 댓글이 달린 글 제목)
     * @param excerpt 태그를 지운 본문 앞부분
     */
    public record TargetSummary(
            String postPublicId,
            String categoryCode,
            String title,
            String excerpt,
            TargetState state,
            String writerNickname) {}
}
