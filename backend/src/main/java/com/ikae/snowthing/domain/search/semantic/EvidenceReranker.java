package com.ikae.snowthing.domain.search.semantic;

import java.util.List;

/**
 * 재정렬기(선택). 결합된 후보를 질문과 함께 다시 정렬한다. 기본 구현은 없다. cross-encoder나 LLM 재정렬은 비용과 지연이 커서, 하이브리드 결과를 먼저 잰
 * 뒤 사용자 승인을 받고 붙인다.
 */
public interface EvidenceReranker {

    /** 재정렬 결과를 비용 메타데이터와 함께 구분하는 이름 */
    String modelId();

    /**
     * @param candidates 결합 순서의 후보
     * @return 새 순서의 근거 ID. 후보에 없는 ID는 무시된다
     */
    List<String> rerank(String query, List<RerankCandidate> candidates);

    record RerankCandidate(String evidenceId, String text) {}
}
