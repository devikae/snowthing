package com.ikae.snowthing.domain.search.repository;

import static com.ikae.snowthing.domain.search.repository.SearchVisibilitySql.COMMENT_VISIBLE;
import static com.ikae.snowthing.domain.search.repository.SearchVisibilitySql.POST_VISIBLE;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 검색 문서의 원문(게시글·댓글)을 공개 범위 조건과 함께 읽는다.
 *
 * <p>엔티티로 읽으면 게시글 이미지·작성자 같은 연관과 LONGTEXT 전체가 영속성 컨텍스트에 올라간다. 색인에는 몇 개 컬럼만 필요하므로 JDBC로 필요한 컬럼만 읽는다.
 */
@Repository
@RequiredArgsConstructor
public class SearchSourceRepository {

    private static final String POST_COLUMNS =
            "SELECT p.post_id, p.public_id, p.title, p.content, p.content_version,"
                    + " pc.code AS category_code, p.created_at, p.updated_at"
                    + " FROM post p JOIN post_category pc ON pc.category_id = p.category_id";

    private static final String COMMENT_COLUMNS =
            "SELECT cm.comment_id, cm.parent_id, cm.content, cm.version, cm.created_at,"
                    + " cm.updated_at, p.post_id, p.public_id AS post_public_id,"
                    + " p.title AS post_title, pc.code AS category_code"
                    + " FROM comment cm JOIN post p ON p.post_id = cm.post_id"
                    + " JOIN post_category pc ON pc.category_id = p.category_id";

    private final JdbcTemplate jdbc;

    public List<PostSource> findVisiblePosts() {
        return jdbc.query(
                POST_COLUMNS + " WHERE " + POST_VISIBLE + " ORDER BY p.post_id",
                SearchSourceRepository::mapPost);
    }

    public Optional<PostSource> findVisiblePost(long postId) {
        return jdbc
                .query(
                        POST_COLUMNS + " WHERE p.post_id = ? AND " + POST_VISIBLE,
                        SearchSourceRepository::mapPost,
                        postId)
                .stream()
                .findFirst();
    }

    public List<CommentSource> findVisibleComments() {
        return jdbc.query(
                COMMENT_COLUMNS
                        + " WHERE "
                        + COMMENT_VISIBLE
                        + " AND "
                        + POST_VISIBLE
                        + " ORDER BY cm.comment_id",
                SearchSourceRepository::mapComment);
    }

    public Optional<CommentSource> findVisibleComment(long commentId) {
        return jdbc
                .query(
                        COMMENT_COLUMNS
                                + " WHERE cm.comment_id = ? AND "
                                + COMMENT_VISIBLE
                                + " AND "
                                + POST_VISIBLE,
                        SearchSourceRepository::mapComment,
                        commentId)
                .stream()
                .findFirst();
    }

    private static PostSource mapPost(ResultSet rs, int rowNum) throws SQLException {
        return new PostSource(
                rs.getLong("post_id"),
                rs.getString("public_id"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getLong("content_version"),
                rs.getString("category_code"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class));
    }

    private static CommentSource mapComment(ResultSet rs, int rowNum) throws SQLException {
        return new CommentSource(
                rs.getLong("comment_id"),
                rs.getObject("parent_id", Long.class),
                rs.getString("content"),
                rs.getLong("version"),
                rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class),
                rs.getLong("post_id"),
                rs.getString("post_public_id"),
                rs.getString("post_title"),
                rs.getString("category_code"));
    }

    public record PostSource(
            long postId,
            String publicId,
            String title,
            String content,
            long contentVersion,
            String categoryCode,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}

    public record CommentSource(
            long commentId,
            Long parentCommentId,
            String content,
            long version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long postId,
            String postPublicId,
            String postTitle,
            String categoryCode) {}
}
