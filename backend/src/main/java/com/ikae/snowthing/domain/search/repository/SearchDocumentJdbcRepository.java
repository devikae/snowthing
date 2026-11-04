package com.ikae.snowthing.domain.search.repository;

import static com.ikae.snowthing.domain.search.repository.SearchVisibilitySql.COMMENT_VISIBLE;
import static com.ikae.snowthing.domain.search.repository.SearchVisibilitySql.POST_VISIBLE;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.search.entity.SearchSourceType;

import lombok.RequiredArgsConstructor;

/**
 * search_document 쓰기와 FULLTEXT 검색. MATCH ... AGAINST는 JPQL·QueryDSL로 표현할 수 없어 JDBC로 쓴다.
 *
 * <p>InnoDB FULLTEXT 인덱스는 커밋된 행만 본다. 같은 트랜잭션 안에서 넣은 문서는 커밋 전까지 검색되지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class SearchDocumentJdbcRepository {

    private static final String MATCH =
            "MATCH(d.title, d.body) AGAINST (? IN NATURAL LANGUAGE MODE)";

    private static final String INSERT_SQL =
            "INSERT INTO search_document (evidence_id, source_type, source_id, chunk_no, post_id,"
                    + " post_public_id, parent_comment_id, category_code, source_version,"
                    + " chunk_hash, title, body, source_created_at, source_updated_at, indexed_at)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /** 검색 결과 행 컬럼. 키워드 검색과 의미 검색(SearchEmbeddingJdbcRepository)이 같은 행 모양을 쓴다. */
    static final String RESULT_COLUMNS =
            "SELECT d.document_id, d.evidence_id, d.source_type, d.source_id, d.chunk_no,"
                    + " d.post_public_id, d.parent_comment_id, d.source_version, d.chunk_hash,"
                    + " d.title, d.body, d.source_created_at, d.source_updated_at, d.indexed_at,"
                    + " p.title AS post_title, pc.code AS category_code";

    /** 원문 테이블 조인. 공개 범위는 {@link #VISIBLE_WHERE}로 건다. */
    static final String VISIBLE_JOINS =
            " JOIN post p ON p.post_id = d.post_id"
                    + " JOIN post_category pc ON pc.category_id = p.category_id"
                    + " LEFT JOIN comment cm"
                    + " ON d.source_type = 'COMMENT' AND cm.comment_id = d.source_id";

    static final String VISIBLE_WHERE =
            POST_VISIBLE + " AND (d.source_type = 'POST' OR (" + COMMENT_VISIBLE + "))";

    private static final String SEARCH_SQL_PREFIX =
            RESULT_COLUMNS
                    + " FROM search_document d"
                    + VISIBLE_JOINS
                    + " WHERE "
                    + MATCH
                    + " AND "
                    + VISIBLE_WHERE;

    private final JdbcTemplate jdbc;

    /**
     * 전체 재구축용. 행이 있으면 동시에 들어온 다른 재구축은 이 DELETE의 행 잠금에서 기다린다. 테이블이 비어 있을 때 겹치면 InnoDB가 한쪽을 교착 상태로
     * 되돌릴 수 있다. 1단계는 관리자 수동 실행이라 별도 잠금을 두지 않는다.
     */
    public int deleteAll() {
        return jdbc.update("DELETE FROM search_document");
    }

    /** 게시글 구간과 그 글의 댓글 문서를 함께 지운다. */
    public int deleteByPostId(long postId) {
        return jdbc.update("DELETE FROM search_document WHERE post_id = ?", postId);
    }

    public int deleteBySource(SearchSourceType type, long sourceId) {
        return jdbc.update(
                "DELETE FROM search_document WHERE source_type = ? AND source_id = ?",
                type.name(),
                sourceId);
    }

    public List<Long> findCommentIdsOfPost(long postId) {
        return jdbc.queryForList(
                "SELECT DISTINCT source_id FROM search_document"
                        + " WHERE post_id = ? AND source_type = 'COMMENT' ORDER BY source_id",
                Long.class,
                postId);
    }

    public Optional<Long> findPostIdOfComment(long commentId) {
        return jdbc
                .queryForList(
                        "SELECT post_id FROM search_document"
                                + " WHERE source_type = 'COMMENT' AND source_id = ? LIMIT 1",
                        Long.class,
                        commentId)
                .stream()
                .findFirst();
    }

    public void insertAll(List<NewSearchDocument> documents) {
        jdbc.batchUpdate(
                INSERT_SQL,
                documents,
                200,
                (ps, doc) -> {
                    ps.setString(1, doc.evidenceId());
                    ps.setString(2, doc.sourceType().name());
                    ps.setLong(3, doc.sourceId());
                    ps.setInt(4, doc.chunkNo());
                    ps.setLong(5, doc.postId());
                    ps.setString(6, doc.postPublicId());
                    if (doc.parentCommentId() == null) {
                        ps.setNull(7, Types.BIGINT);
                    } else {
                        ps.setLong(7, doc.parentCommentId());
                    }
                    ps.setString(8, doc.categoryCode());
                    ps.setLong(9, doc.sourceVersion());
                    ps.setString(10, doc.chunkHash());
                    ps.setString(11, doc.title());
                    ps.setString(12, doc.body());
                    ps.setObject(13, doc.sourceCreatedAt());
                    ps.setObject(14, doc.sourceUpdatedAt());
                    ps.setObject(15, doc.indexedAt());
                });
    }

    /**
     * 공개 범위를 후보 생성 쿼리에서 바로 거른다. 색인 뒤에 숨김·삭제된 원문은 원문 테이블 조인 조건에서 빠진다. 점수는 정렬에만 쓰고 반환하지 않는다. 같은 점수는
     * document_id로 순서를 고정한다.
     *
     * <p>createdBefore·excludePostPublicId는 평가용 조건이다. 질문 시점 이후에 작성된 문서와 질문 자신의 글타래를 빼서, 질문 당시 존재하던
     * 다른 글로만 답을 찾을 수 있는지 본다.
     */
    public List<EvidenceSearchRow> search(
            String query,
            String categoryCode,
            LocalDateTime createdBefore,
            String excludePostPublicId,
            int size) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SEARCH_SQL_PREFIX);
        args.add(query);
        appendFilters(sql, args, categoryCode, createdBefore, excludePostPublicId);
        sql.append(" ORDER BY ").append(MATCH).append(" DESC, d.document_id ASC LIMIT ?");
        args.add(query);
        args.add(size);
        return jdbc.query(sql.toString(), SearchDocumentJdbcRepository::mapRow, args.toArray());
    }

    /** 카테고리와 평가용 조건. 키워드 검색과 의미 검색이 같은 조건을 쓴다. */
    static void appendFilters(
            StringBuilder sql,
            List<Object> args,
            String categoryCode,
            LocalDateTime createdBefore,
            String excludePostPublicId) {
        if (categoryCode != null) {
            sql.append(" AND pc.code = ?");
            args.add(categoryCode);
        }
        if (createdBefore != null) {
            sql.append(" AND d.source_created_at < ?");
            args.add(createdBefore);
        }
        if (excludePostPublicId != null) {
            sql.append(" AND d.post_public_id <> ?");
            args.add(excludePostPublicId);
        }
    }

    static EvidenceSearchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new EvidenceSearchRow(
                rs.getLong("document_id"),
                rs.getString("evidence_id"),
                SearchSourceType.valueOf(rs.getString("source_type")),
                rs.getLong("source_id"),
                rs.getInt("chunk_no"),
                rs.getString("post_public_id"),
                rs.getObject("parent_comment_id", Long.class),
                rs.getLong("source_version"),
                rs.getString("chunk_hash"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("post_title"),
                rs.getString("category_code"),
                rs.getObject("source_created_at", LocalDateTime.class),
                rs.getObject("source_updated_at", LocalDateTime.class),
                rs.getObject("indexed_at", LocalDateTime.class));
    }

    public record NewSearchDocument(
            String evidenceId,
            SearchSourceType sourceType,
            long sourceId,
            int chunkNo,
            long postId,
            String postPublicId,
            Long parentCommentId,
            String categoryCode,
            long sourceVersion,
            String chunkHash,
            String title,
            String body,
            LocalDateTime sourceCreatedAt,
            LocalDateTime sourceUpdatedAt,
            LocalDateTime indexedAt) {}

    /** @param documentId 같은 점수일 때 순서를 고정하는 보조 기준. 응답에는 넣지 않는다 */
    public record EvidenceSearchRow(
            long documentId,
            String evidenceId,
            SearchSourceType sourceType,
            long sourceId,
            int chunkNo,
            String postPublicId,
            Long parentCommentId,
            long sourceVersion,
            String chunkHash,
            String indexedTitle,
            String indexedBody,
            String postTitle,
            String categoryCode,
            LocalDateTime sourceCreatedAt,
            LocalDateTime sourceUpdatedAt,
            LocalDateTime indexedAt) {}
}
