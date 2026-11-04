package com.ikae.snowthing.domain.search.repository;

import static com.ikae.snowthing.domain.search.repository.SearchVisibilitySql.COMMENT_VISIBLE;
import static com.ikae.snowthing.domain.search.repository.SearchVisibilitySql.POST_VISIBLE;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/**
 * 원문·색인 상태·문서를 비교하는 대조 쿼리와 재인덱싱 대상 조회.
 *
 * <p>돌려주는 version은 GREATEST(현재 원문 버전, 적용 버전)이다. 처리기는 이벤트 버전이 적용 버전보다 작으면 건너뛰므로, 상태 행의 적용 버전이 잘못 커져
 * 있어도 대조 이벤트가 버려지지 않게 한다. 처리기는 실제 반영 시 적용 버전을 현재 원문 버전으로 다시 맞춘다.
 *
 * <p>이미 PENDING 이벤트가 있는 원문은 빼서 같은 원문을 중복으로 넣지 않는다. 대기 중인 이벤트가 처리될 때 현재 원문을 다시 읽기 때문이다.
 */
@Repository
@RequiredArgsConstructor
public class SearchConsistencyRepository {

    private static final String NO_PENDING_POST =
            " AND NOT EXISTS (SELECT 1 FROM search_outbox o WHERE o.source_type = 'POST'"
                    + " AND o.source_id = %s AND o.status = 'PENDING')";

    private static final String NO_PENDING_COMMENT =
            " AND NOT EXISTS (SELECT 1 FROM search_outbox o WHERE o.source_type = 'COMMENT'"
                    + " AND o.source_id = %s AND o.status = 'PENDING')";

    /** 공개 게시글인데 상태가 없거나, tombstone이거나, 버전이 다르거나, 현재 버전 문서 수가 상태와 다르거나, 다른 버전 문서가 남은 경우. */
    private static final String POST_UPSERT_DRIFT =
            "SELECT p.post_id AS source_id,"
                    + " GREATEST(p.content_version, COALESCE(s.applied_version, -1)) AS version"
                    + " FROM post p JOIN post_category pc ON pc.category_id = p.category_id"
                    + " LEFT JOIN search_index_state s"
                    + " ON s.source_type = 'POST' AND s.source_id = p.post_id"
                    + " WHERE "
                    + POST_VISIBLE
                    + " AND (s.source_id IS NULL OR s.tombstoned"
                    + " OR s.applied_version <> p.content_version"
                    + " OR s.document_count <> (SELECT COUNT(*) FROM search_document d"
                    + " WHERE d.source_type = 'POST' AND d.source_id = p.post_id"
                    + " AND d.source_version = p.content_version)"
                    + " OR EXISTS (SELECT 1 FROM search_document d"
                    + " WHERE d.source_type = 'POST' AND d.source_id = p.post_id"
                    + " AND d.source_version <> p.content_version))"
                    + String.format(NO_PENDING_POST, "p.post_id")
                    + " ORDER BY p.post_id";

    /** 공개가 아닌(또는 없는) 게시글인데 상태가 tombstone이 아니거나 문서가 남아 있는 경우. */
    private static final String POST_DELETE_DRIFT =
            "SELECT x.source_id, GREATEST(COALESCE(MAX(p.content_version), -1),"
                    + " COALESCE(MAX(s.applied_version), -1), MAX(x.doc_version)) AS version"
                    + " FROM ("
                    + "SELECT s0.source_id, -1 AS doc_version FROM search_index_state s0"
                    + " WHERE s0.source_type = 'POST' AND s0.tombstoned = false"
                    + " UNION ALL"
                    + " SELECT d.source_id, d.source_version FROM search_document d"
                    + " WHERE d.source_type = 'POST'"
                    + ") x"
                    + " LEFT JOIN post p ON p.post_id = x.source_id"
                    + " LEFT JOIN search_index_state s"
                    + " ON s.source_type = 'POST' AND s.source_id = x.source_id"
                    + " WHERE NOT EXISTS (SELECT 1 FROM post p JOIN post_category pc"
                    + " ON pc.category_id = p.category_id WHERE p.post_id = x.source_id AND "
                    + POST_VISIBLE
                    + ")"
                    + String.format(NO_PENDING_POST, "x.source_id")
                    + " GROUP BY x.source_id ORDER BY x.source_id";

    private static final String COMMENT_UPSERT_DRIFT =
            "SELECT cm.comment_id AS source_id,"
                    + " GREATEST(cm.version, COALESCE(s.applied_version, -1)) AS version"
                    + " FROM comment cm JOIN post p ON p.post_id = cm.post_id"
                    + " JOIN post_category pc ON pc.category_id = p.category_id"
                    + " LEFT JOIN search_index_state s"
                    + " ON s.source_type = 'COMMENT' AND s.source_id = cm.comment_id"
                    + " WHERE "
                    + COMMENT_VISIBLE
                    + " AND "
                    + POST_VISIBLE
                    + " AND (s.source_id IS NULL OR s.tombstoned"
                    + " OR s.applied_version <> cm.version"
                    + " OR s.document_count <> (SELECT COUNT(*) FROM search_document d"
                    + " WHERE d.source_type = 'COMMENT' AND d.source_id = cm.comment_id"
                    + " AND d.source_version = cm.version)"
                    + " OR EXISTS (SELECT 1 FROM search_document d"
                    + " WHERE d.source_type = 'COMMENT' AND d.source_id = cm.comment_id"
                    + " AND d.source_version <> cm.version))"
                    + String.format(NO_PENDING_COMMENT, "cm.comment_id")
                    + " ORDER BY cm.comment_id";

    private static final String COMMENT_DELETE_DRIFT =
            "SELECT x.source_id, GREATEST(COALESCE(MAX(c.version), -1),"
                    + " COALESCE(MAX(s.applied_version), -1), MAX(x.doc_version)) AS version"
                    + " FROM ("
                    + "SELECT s0.source_id, -1 AS doc_version FROM search_index_state s0"
                    + " WHERE s0.source_type = 'COMMENT' AND s0.tombstoned = false"
                    + " UNION ALL"
                    + " SELECT d.source_id, d.source_version FROM search_document d"
                    + " WHERE d.source_type = 'COMMENT'"
                    + ") x"
                    + " LEFT JOIN comment c ON c.comment_id = x.source_id"
                    + " LEFT JOIN search_index_state s"
                    + " ON s.source_type = 'COMMENT' AND s.source_id = x.source_id"
                    + " WHERE NOT EXISTS (SELECT 1 FROM comment cm JOIN post p"
                    + " ON p.post_id = cm.post_id JOIN post_category pc"
                    + " ON pc.category_id = p.category_id WHERE cm.comment_id = x.source_id AND "
                    + COMMENT_VISIBLE
                    + " AND "
                    + POST_VISIBLE
                    + ")"
                    + String.format(NO_PENDING_COMMENT, "x.source_id")
                    + " GROUP BY x.source_id ORDER BY x.source_id";

    private static final String VISIBLE_POSTS_AFTER =
            "SELECT p.post_id AS source_id,"
                    + " GREATEST(p.content_version, COALESCE(s.applied_version, -1)) AS version"
                    + " FROM post p JOIN post_category pc ON pc.category_id = p.category_id"
                    + " LEFT JOIN search_index_state s"
                    + " ON s.source_type = 'POST' AND s.source_id = p.post_id"
                    + " WHERE p.post_id > ? AND "
                    + POST_VISIBLE
                    + " ORDER BY p.post_id LIMIT ?";

    private static final RowMapper<SourceVersion> MAPPER =
            (rs, rowNum) -> new SourceVersion(rs.getLong("source_id"), rs.getLong("version"));

    private final JdbcTemplate jdbc;

    public List<SourceVersion> findPostUpsertDrift() {
        return jdbc.query(POST_UPSERT_DRIFT, MAPPER);
    }

    public List<SourceVersion> findPostDeleteDrift() {
        return jdbc.query(POST_DELETE_DRIFT, MAPPER);
    }

    public List<SourceVersion> findCommentUpsertDrift() {
        return jdbc.query(COMMENT_UPSERT_DRIFT, MAPPER);
    }

    public List<SourceVersion> findCommentDeleteDrift() {
        return jdbc.query(COMMENT_DELETE_DRIFT, MAPPER);
    }

    public List<SourceVersion> findVisiblePostsAfter(long lastPostId, int limit) {
        return jdbc.query(VISIBLE_POSTS_AFTER, MAPPER, lastPostId, limit);
    }

    public record SourceVersion(long sourceId, long version) {}
}
