package com.ikae.snowthing.domain.contentfilter.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.contentfilter.dto.FilterHoldItem;
import com.ikae.snowthing.domain.contentfilter.entity.FilterTargetType;

import lombok.RequiredArgsConstructor;

/** 관리자 검토 목록 조회. 글, 댓글, 작성자를 한 번에 붙여 읽는다. 본문은 DB에서 앞부분만 잘라 온다. */
@Repository
@RequiredArgsConstructor
public class FilterHoldQueryRepository {

    private static final int EXCERPT_SOURCE_LENGTH = 2000;
    private static final int EXCERPT_LENGTH = 300;
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\s+");

    private final NamedParameterJdbcTemplate jdbc;

    public List<FilterHoldItem> findPage(int offset, int limit) {
        String sql =
                """
                SELECT h.content_filter_hold_id, h.target_type, h.target_id, h.matched_word,
                       h.writer_ip, h.held_at,
                       p.public_id AS post_public_id, p.title AS post_title, pc.code AS category_code,
                       CASE WHEN h.target_type = 'COMMENT' THEN c.content
                            ELSE LEFT(p.content, :sourceLength) END AS body,
                       CASE WHEN h.target_type = 'COMMENT' THEN c.is_anonymous
                            ELSE p.is_anonymous END AS anonymous,
                       m.nickname, m.public_id AS member_public_id
                FROM content_filter_hold h
                JOIN post p ON p.post_id = h.post_id
                JOIN post_category pc ON pc.category_id = p.category_id
                LEFT JOIN comment c ON h.target_type = 'COMMENT' AND c.comment_id = h.target_id
                LEFT JOIN member m ON m.member_id = h.member_id
                ORDER BY h.held_at DESC, h.content_filter_hold_id DESC
                LIMIT :limit OFFSET :offset
                """;
        return jdbc.query(
                sql,
                new MapSqlParameterSource("limit", limit)
                        .addValue("offset", offset)
                        .addValue("sourceLength", EXCERPT_SOURCE_LENGTH),
                (rs, row) -> {
                    FilterTargetType type = FilterTargetType.valueOf(rs.getString("target_type"));
                    return new FilterHoldItem(
                            rs.getLong("content_filter_hold_id"),
                            type,
                            rs.getString("post_public_id"),
                            type == FilterTargetType.COMMENT ? rs.getLong("target_id") : null,
                            rs.getString("category_code"),
                            rs.getString("post_title"),
                            excerpt(rs.getString("body")),
                            rs.getString("matched_word"),
                            rs.getString("nickname"),
                            rs.getString("member_public_id"),
                            rs.getBoolean("anonymous"),
                            rs.getString("writer_ip"),
                            rs.getObject("held_at", LocalDateTime.class));
                });
    }

    static String excerpt(String body) {
        if (body == null) return "";
        String text = SPACES.matcher(TAG.matcher(body).replaceAll(" ")).replaceAll(" ").strip();
        return text.length() > EXCERPT_LENGTH ? text.substring(0, EXCERPT_LENGTH) + "…" : text;
    }
}
