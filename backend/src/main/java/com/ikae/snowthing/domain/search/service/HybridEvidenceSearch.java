package com.ikae.snowthing.domain.search.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.search.dto.EvidenceSearchCondition;
import com.ikae.snowthing.domain.search.dto.SearchMode;
import com.ikae.snowthing.domain.search.dto.SearchUsage;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository.EvidenceSearchRow;
import com.ikae.snowthing.domain.search.repository.SearchEmbeddingJdbcRepository;
import com.ikae.snowthing.domain.search.repository.SearchEmbeddingJdbcRepository.VectorCandidate;
import com.ikae.snowthing.domain.search.semantic.EmbeddingProvider;
import com.ikae.snowthing.domain.search.semantic.EmbeddingProviderException;
import com.ikae.snowthing.domain.search.semantic.EmbeddingResult;
import com.ikae.snowthing.domain.search.semantic.EvidenceReranker;
import com.ikae.snowthing.domain.search.semantic.EvidenceReranker.RerankCandidate;
import com.ikae.snowthing.domain.search.semantic.ReciprocalRankFusion;
import com.ikae.snowthing.domain.search.semantic.SemanticSearchSupport;
import com.ikae.snowthing.domain.search.semantic.VectorMath;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/**
 * 하이브리드 근거 검색 (IKA-11 비교 실험).
 *
 * <ol>
 *   <li>키워드 상위 K: 1단계 FULLTEXT 검색 그대로 (같은 공개 범위와 조건)
 *   <li>벡터 상위 K: 질문을 한 번 임베딩하고, 공개 범위와 조건을 통과한 현재 벡터 전체와 코사인 유사도를 앱에서 계산한다. 같으면 document_id 순
 *   <li>RRF(k)로 두 순위를 결합한다. 서로 단위가 다른 점수는 더하지 않는다
 *   <li>재정렬 방식이면 결합 상위 K를 재정렬기에 넘긴다
 * </ol>
 *
 * <p>결과 행은 두 검색 모두 같은 원문 조인과 {@code SearchVisibilitySql} 조건을 거친 행이므로 숨김, 삭제, MARKET 원문은 어느 경로로도 들어오지
 * 않는다. 점수는 응답에 넣지 않는다.
 */
@Component
@RequiredArgsConstructor
public class HybridEvidenceSearch {

    private final SearchDocumentJdbcRepository documents;
    private final SearchEmbeddingJdbcRepository embeddings;
    private final SemanticSearchSupport semantic;

    public Result search(EvidenceSearchCondition condition, SearchMode mode) {
        EmbeddingProvider provider = semantic.provider();
        EvidenceReranker reranker =
                mode == SearchMode.HYBRID_RERANK
                        ? semantic.reranker()
                                .orElseThrow(
                                        () -> new CustomException(ErrorCode.SEARCH_MODE_UNAVAILABLE))
                        : null;
        int candidateK = semantic.properties().effectiveCandidateK();

        List<EvidenceSearchRow> keyword =
                documents.search(
                        condition.query(),
                        condition.categoryCode(),
                        condition.createdBefore(),
                        condition.excludePostPublicId(),
                        candidateK);

        EmbeddingResult embedded;
        try {
            embedded = provider.embed(List.of(condition.query()));
        } catch (EmbeddingProviderException e) {
            throw new CustomException(ErrorCode.SEARCH_PROVIDER_FAILED);
        }
        if (embedded.vectors().size() != 1
                || embedded.vectors().getFirst().length != provider.dimension()) {
            throw new CustomException(ErrorCode.SEARCH_PROVIDER_FAILED);
        }
        float[] queryVector = embedded.vectors().getFirst();

        List<VectorCandidate> candidates =
                embeddings.findVisibleVectors(
                        provider.modelId(),
                        provider.dimension(),
                        condition.categoryCode(),
                        condition.createdBefore(),
                        condition.excludePostPublicId());
        List<EvidenceSearchRow> vector = topByCosine(queryVector, candidates, candidateK);

        Map<String, EvidenceSearchRow> rows = new HashMap<>();
        keyword.forEach(row -> rows.putIfAbsent(row.evidenceId(), row));
        vector.forEach(row -> rows.putIfAbsent(row.evidenceId(), row));
        List<String> fused =
                ReciprocalRankFusion.fuse(
                        List.of(ids(keyword), ids(vector)), semantic.properties().effectiveRrfK());

        int rerankCalls = 0;
        if (reranker != null) {
            List<String> head = fused.subList(0, Math.min(candidateK, fused.size()));
            List<RerankCandidate> input =
                    head.stream().map(id -> new RerankCandidate(id, text(rows.get(id)))).toList();
            try {
                fused = reorder(head, reranker.rerank(condition.query(), input));
            } catch (RuntimeException e) {
                throw new CustomException(ErrorCode.SEARCH_PROVIDER_FAILED);
            }
            rerankCalls = 1;
        }

        List<EvidenceSearchRow> result =
                fused.stream().limit(condition.size()).map(rows::get).toList();
        SearchUsage usage =
                new SearchUsage(
                        provider.modelId(),
                        1,
                        embedded.inputTokens(),
                        rerankCalls,
                        keyword.size(),
                        candidates.size());
        return new Result(result, usage);
    }

    /** 코사인 유사도 내림차순, 같으면 document_id 오름차순. */
    static List<EvidenceSearchRow> topByCosine(
            float[] query, List<VectorCandidate> candidates, int limit) {
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (VectorCandidate candidate : candidates) {
            scored.add(
                    new Scored(
                            candidate.row(),
                            VectorMath.cosine(query, VectorMath.fromBytes(candidate.vector()))));
        }
        scored.sort(
                Comparator.comparingDouble(Scored::score)
                        .reversed()
                        .thenComparingLong(item -> item.row().documentId()));
        return scored.stream().limit(limit).map(Scored::row).toList();
    }

    /** 재정렬기가 돌려준 순서를 쓰되, 후보에 없는 ID는 버리고 빠진 후보는 원래 순서로 뒤에 붙인다. */
    static List<String> reorder(List<String> candidates, List<String> reranked) {
        Set<String> allowed = new LinkedHashSet<>(candidates);
        Set<String> ordered = new LinkedHashSet<>();
        if (reranked != null) {
            for (String id : reranked) {
                if (allowed.contains(id)) {
                    ordered.add(id);
                }
            }
        }
        ordered.addAll(allowed);
        return List.copyOf(ordered);
    }

    private static List<String> ids(List<EvidenceSearchRow> rows) {
        return rows.stream().map(EvidenceSearchRow::evidenceId).toList();
    }

    private static String text(EvidenceSearchRow row) {
        return row.indexedTitle() != null ? row.indexedTitle() : row.indexedBody();
    }

    private record Scored(EvidenceSearchRow row, double score) {}

    public record Result(List<EvidenceSearchRow> rows, SearchUsage usage) {}
}
