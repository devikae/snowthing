package com.ikae.snowthing.domain.search.dto;

import java.util.List;

/**
 * @param mode 실제로 쓴 검색 방식. 평가 실행기는 요청한 방식과 같은지 확인한다
 * @param usage 비용과 후보 메타데이터
 */
public record EvidenceSearchResponse(
        String query,
        int size,
        SearchMode mode,
        SearchUsage usage,
        List<EvidenceSearchItemResponse> items) {

    public EvidenceSearchResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
