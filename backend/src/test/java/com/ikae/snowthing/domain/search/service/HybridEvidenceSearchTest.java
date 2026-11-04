package com.ikae.snowthing.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository.EvidenceSearchRow;
import com.ikae.snowthing.domain.search.repository.SearchEmbeddingJdbcRepository.VectorCandidate;
import com.ikae.snowthing.domain.search.semantic.VectorMath;

class HybridEvidenceSearchTest {

    @Test
    @DisplayName("벡터 순위는 코사인 내림차순, 같으면 document_id 오름차순")
    void cosineTopKTieBreaksByDocumentId() {
        float[] query = {1, 0};
        List<VectorCandidate> candidates =
                List.of(
                        candidate(30, "P:3:1", new float[] {1, 0}),
                        candidate(10, "P:1:1", new float[] {0, 1}),
                        candidate(20, "P:2:1", new float[] {2, 0}),
                        candidate(5, "C:9:0", new float[] {1, 1}));

        List<String> top =
                HybridEvidenceSearch.topByCosine(query, candidates, 3).stream()
                        .map(EvidenceSearchRow::evidenceId)
                        .toList();

        assertThat(top).containsExactly("P:2:1", "P:3:1", "C:9:0");
    }

    @Test
    @DisplayName("재정렬 결과에서 후보 밖 ID는 버리고 빠진 후보는 원래 순서로 뒤에 붙인다")
    void rerankReorder() {
        assertThat(
                        HybridEvidenceSearch.reorder(
                                List.of("a", "b", "c", "d"), List.of("c", "zzz", "a", "c")))
                .containsExactly("c", "a", "b", "d");
        assertThat(HybridEvidenceSearch.reorder(List.of("a", "b"), null))
                .containsExactly("a", "b");
    }

    private static VectorCandidate candidate(long documentId, String evidenceId, float[] vector) {
        LocalDateTime now = LocalDateTime.of(2026, 11, 4, 12, 0);
        EvidenceSearchRow row =
                new EvidenceSearchRow(
                        documentId,
                        evidenceId,
                        evidenceId.startsWith("C") ? SearchSourceType.COMMENT : SearchSourceType.POST,
                        1L,
                        evidenceId.startsWith("C") ? 0 : 1,
                        "00000000-0000-0000-0000-000000000001",
                        null,
                        0,
                        "0".repeat(64),
                        null,
                        "본문",
                        "제목",
                        "QNA",
                        now,
                        now,
                        now);
        return new VectorCandidate(row, VectorMath.toBytes(vector));
    }

}
