package com.ikae.snowthing.domain.aimember;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.aimember.AiReactionPolicy.CommentView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.PostView;

import lombok.RequiredArgsConstructor;

/** AI 멤버 판단용 읽기 전용 조회. 상태를 따로 저장하지 않고 post·comment를 작성자 기준으로 센다. */
@Repository
@RequiredArgsConstructor
public class AiMemberQueryRepository {

    static final List<String> BOARDS = List.of("FREE", "ANONYMOUS", "QNA");

    static final int MAX_RECENT_POSTS = 200;

    private final NamedParameterJdbcTemplate jdbc;

    public Map<Long, Integer> countPostsSince(Collection<Long> memberIds, LocalDateTime since) {
        return countSince("post", memberIds, since);
    }

    public Map<Long, Integer> countCommentsSince(Collection<Long> memberIds, LocalDateTime since) {
        return countSince("comment", memberIds, since);
    }

    /**
     * 세 게시판의 정상 글. 오래된 글부터.
     *
     * <p>1분마다 불리므로 글이 몰려도 최근 {@value #MAX_RECENT_POSTS}개만 읽는다. 본문은 정치 글 거르기에 전부 쓰이므로 자르지 않는다.
     */
    public List<PostView> findRecentPosts(LocalDateTime since) {
        return jdbc.query(
                "SELECT * FROM (SELECT p.post_id, p.public_id, p.member_id, c.code, p.title,"
                        + " p.content, p.created_at FROM post p JOIN post_category c ON"
                        + " c.category_id = p.category_id WHERE c.code IN (:boards) AND p.status ="
                        + " 'NORMAL' AND p.is_deleted = false AND p.created_at >= :since"
                        + " ORDER BY p.created_at DESC, p.post_id DESC LIMIT :limit) recent"
                        + " ORDER BY created_at, post_id",
                new MapSqlParameterSource("boards", BOARDS)
                        .addValue("since", since)
                        .addValue("limit", MAX_RECENT_POSTS),
                (rs, row) ->
                        new PostView(
                                rs.getLong("post_id"),
                                rs.getString("public_id"),
                                (Long) rs.getObject("member_id", Long.class),
                                rs.getString("code"),
                                rs.getString("title"),
                                rs.getString("content"),
                                rs.getObject("created_at", LocalDateTime.class)));
    }

    /** 삭제되지 않은 댓글. 작성 순서대로. */
    public List<CommentView> findComments(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT comment_id, post_id, member_id, parent_id, content, is_anonymous,"
                        + " created_at FROM comment WHERE post_id IN (:postIds)"
                        + " AND is_deleted = false ORDER BY created_at, comment_id",
                new MapSqlParameterSource("postIds", postIds),
                (rs, row) ->
                        new CommentView(
                                rs.getLong("comment_id"),
                                rs.getLong("post_id"),
                                (Long) rs.getObject("member_id", Long.class),
                                (Long) rs.getObject("parent_id", Long.class),
                                rs.getString("content"),
                                rs.getBoolean("is_anonymous"),
                                rs.getObject("created_at", LocalDateTime.class)));
    }

    public List<String> findTitlesSince(Collection<Long> memberIds, LocalDateTime since) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList(
                "SELECT title FROM post WHERE member_id IN (:ids) AND created_at >= :since"
                        + " ORDER BY created_at DESC, post_id DESC LIMIT 100",
                new MapSqlParameterSource("ids", memberIds).addValue("since", since),
                String.class);
    }

    /** AI 멤버가 최근에 쓴 글과 댓글 본문. 유사도 검사에 쓴다. */
    public List<String> findRecentContents(Collection<Long> memberIds, LocalDateTime since) {
        if (memberIds.isEmpty()) {
            return List.of();
        }
        return jdbc.queryForList(
                "(SELECT content FROM post WHERE member_id IN (:ids) AND created_at >= :since"
                        + " ORDER BY created_at DESC, post_id DESC LIMIT 100)"
                        + " UNION ALL"
                        + " (SELECT content FROM comment WHERE member_id IN (:ids)"
                        + " AND created_at >= :since"
                        + " ORDER BY created_at DESC, comment_id DESC LIMIT 200)",
                new MapSqlParameterSource("ids", memberIds).addValue("since", since),
                String.class);
    }

    /** 최근 설질 제보 "리조트: 내용" */
    public List<String> findRecentSnowReports(LocalDateTime since) {
        return jdbc.queryForList(
                "SELECT CONCAT(r.name, ': ', rr.content) FROM resort_report rr JOIN resort r"
                        + " ON r.resort_id = rr.resort_id WHERE rr.status = 'NORMAL'"
                        + " AND rr.created_at >= :since"
                        + " ORDER BY rr.created_at DESC, rr.report_id DESC LIMIT 10",
                new MapSqlParameterSource("since", since),
                String.class);
    }

    public List<String> findResortNames() {
        return jdbc.queryForList(
                "SELECT name FROM resort ORDER BY resort_id LIMIT 30",
                new MapSqlParameterSource(),
                String.class);
    }

    /** 다른 회원 글·댓글에 같은 IP 앞 두 자리가 있으면 true. 화면에는 앞 두 자리만 보이므로 겹치면 같은 사람처럼 보인다. */
    public boolean ipPrefixUsedByOthers(String writerIp, long memberId) {
        String[] parts = writerIp.split("\\.");
        String prefix = parts[0] + "." + parts[1] + ".%";
        MapSqlParameterSource params =
                new MapSqlParameterSource("prefix", prefix).addValue("memberId", memberId);
        Integer found =
                jdbc.queryForObject(
                        "SELECT (EXISTS (SELECT 1 FROM post WHERE writer_ip LIKE :prefix"
                                + " AND (member_id IS NULL OR member_id <> :memberId))"
                                + " OR EXISTS (SELECT 1 FROM comment WHERE writer_ip LIKE :prefix"
                                + " AND (member_id IS NULL OR member_id <> :memberId)))",
                        params,
                        Integer.class);
        return found != null && found > 0;
    }

    private Map<Long, Integer> countSince(
            String table, Collection<Long> memberIds, LocalDateTime since) {
        Map<Long, Integer> counts = new HashMap<>();
        if (memberIds.isEmpty()) {
            return counts;
        }
        jdbc.query(
                "SELECT member_id, COUNT(*) AS cnt FROM "
                        + table
                        + " WHERE member_id IN (:ids) AND created_at >= :since GROUP BY member_id",
                new MapSqlParameterSource("ids", memberIds).addValue("since", since),
                rs -> {
                    counts.put(rs.getLong("member_id"), rs.getInt("cnt"));
                });
        return counts;
    }
}
