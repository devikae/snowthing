package com.ikae.snowthing.domain.search.repository;

import static com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository.RESULT_COLUMNS;
import static com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository.VISIBLE_JOINS;
import static com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository.VISIBLE_WHERE;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository.EvidenceSearchRow;

import lombok.RequiredArgsConstructor;

/**
 * search_embedding 읽기와 쓰기 (IKA-11 의미 검색 실험).
 *
 * <ul>
 *   <li>벡터는 (evidence_id, model_id)마다 하나다. content_hash가 현재 search_document.chunk_hash와 같은 벡터만 "현재
 *       벡터"다.
 *   <li>검색은 키워드 검색과 같은 원문 조인과 공개 범위 조건({@link SearchVisibilitySql}), 같은 평가용 조건을 쓴다.
 *   <li>저장은 "그 구간이 아직 같은 해시일 때만" 한다. 제공자를 부르는 사이 원문이 바뀌었으면 아무것도 쓰지 않는다(오래된 벡터 저장 방지).
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class SearchEmbeddingJdbcRepository {

    private static final String CURRENT_EMBEDDING_JOIN =
            " JOIN search_embedding e ON e.evidence_id = d.evidence_id AND e.model_id = ?"
                    + " AND e.content_hash = d.chunk_hash AND e.dimension = ?";

    private final JdbcTemplate jdbc;

    /** 현재 벡터가 없는 구간(새 구간, 원문이 바뀐 구간, 모델이 바뀐 경우). document_id 순. */
    public List<MissingEmbedding> findMissing(String modelId, int dimension, int limit) {
        return jdbc.query(
                "SELECT d.evidence_id, d.chunk_hash, d.title, d.body FROM search_document d"
                        + " LEFT JOIN search_embedding e ON e.evidence_id = d.evidence_id"
                        + " AND e.model_id = ? AND e.content_hash = d.chunk_hash"
                        + " AND e.dimension = ?"
                        + " WHERE e.embedding_id IS NULL ORDER BY d.document_id LIMIT ?",
                (rs, rowNum) ->
                        new MissingEmbedding(
                                rs.getString("evidence_id"),
                                rs.getString("chunk_hash"),
                                rs.getString("title") != null
                                        ? rs.getString("title")
                                        : rs.getString("body")),
                modelId,
                dimension,
                limit);
    }

    /**
     * 구간이 아직 같은 해시일 때만 넣거나 바꾼다.
     *
     * @return 저장했으면 true. 그 사이 구간이 바뀌었거나 지워졌으면 false
     */
    public boolean saveIfCurrent(
            String evidenceId,
            String contentHash,
            String modelId,
            int dimension,
            byte[] vector,
            LocalDateTime now) {
        int updated =
                jdbc.update(
                        "INSERT INTO search_embedding (evidence_id, model_id, post_id, source_type,"
                                + " source_id, content_hash, dimension, vector, created_at)"
                                + " SELECT d.evidence_id, ?, d.post_id, d.source_type, d.source_id,"
                                + " d.chunk_hash, ?, ?, ? FROM search_document d"
                                + " WHERE d.evidence_id = ? AND d.chunk_hash = ?"
                                + " ON DUPLICATE KEY UPDATE content_hash = ?, dimension = ?,"
                                + " vector = ?, created_at = ?",
                        modelId,
                        dimension,
                        vector,
                        now,
                        evidenceId,
                        contentHash,
                        contentHash,
                        dimension,
                        vector,
                        now);
        return updated > 0;
    }

    /**
     * 게시글 처리 트랜잭션 안에서 부른다. 그 글(과 댓글)의 벡터 중 대응하는 현재 구간이 없는 것(삭제, 비공개, 내용 변경)을 지운다. 지운 원문에서 나온 벡터를
     * 남기지 않기 위해 의미 검색이 꺼져 있어도 지운다.
     */
    public int deleteOutdatedOfPost(long postId) {
        return jdbc.update(
                "DELETE e FROM search_embedding e LEFT JOIN search_document d"
                        + " ON d.evidence_id = e.evidence_id AND d.chunk_hash = e.content_hash"
                        + " WHERE e.post_id = ? AND d.document_id IS NULL",
                postId);
    }

    /** 댓글 처리 트랜잭션 안에서 부른다. {@link #deleteOutdatedOfPost(long)}의 댓글 하나 버전. */
    public int deleteOutdatedOfSource(SearchSourceType type, long sourceId) {
        return jdbc.update(
                "DELETE e FROM search_embedding e LEFT JOIN search_document d"
                        + " ON d.evidence_id = e.evidence_id AND d.chunk_hash = e.content_hash"
                        + " WHERE e.source_type = ? AND e.source_id = ? AND d.document_id IS NULL",
                type.name(),
                sourceId);
    }

    /**
     * 공개 범위와 평가용 조건을 통과한 구간의 현재 벡터를 모두 읽는다. 코사인 계산은 앱에서 한다(MySQL 8.0에 벡터 타입과 벡터 인덱스가 없음). 구간 수에 비례해
     * 읽으므로 수천 구간을 넘으면 다른 저장소가 필요하다(08 문서 §2).
     *
     * @param limit 최대 행 수. 호출자는 상한 + 1을 넘겨 상한 초과 여부를 판단한다
     */
    public List<VectorCandidate> findVisibleVectors(
            String modelId,
            int dimension,
            String categoryCode,
            LocalDateTime createdBefore,
            String excludePostPublicId,
            int limit) {
        List<Object> args = new ArrayList<>();
        args.add(modelId);
        args.add(dimension);
        StringBuilder sql =
                new StringBuilder(RESULT_COLUMNS)
                        .append(", e.vector FROM search_document d")
                        .append(CURRENT_EMBEDDING_JOIN)
                        .append(VISIBLE_JOINS)
                        .append(" WHERE ")
                        .append(VISIBLE_WHERE);
        SearchDocumentJdbcRepository.appendFilters(
                sql, args, categoryCode, createdBefore, excludePostPublicId);
        sql.append(" ORDER BY d.document_id LIMIT ?");
        args.add(limit);
        return jdbc.query(
                sql.toString(),
                (rs, rowNum) ->
                        new VectorCandidate(
                                SearchDocumentJdbcRepository.mapRow(rs, rowNum),
                                rs.getBytes("vector")),
                args.toArray());
    }

    public record MissingEmbedding(String evidenceId, String contentHash, String text) {}

    public record VectorCandidate(EvidenceSearchRow row, byte[] vector) {}
}
