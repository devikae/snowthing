package com.ikae.snowthing.domain.search.dto;

/**
 * 검색 한 번의 비용과 후보 메타데이터. 평가 실행기가 방식별 비용을 합산하는 데 쓴다. 점수는 넣지 않는다.
 *
 * @param embeddingModel 질문 벡터를 만든 모델. 키워드 방식이면 null
 * @param embeddingCalls 임베딩 제공자 호출 수 (질문 1건 = 1회)
 * @param embeddingInputTokens 제공자가 알려 준 입력 토큰 수. 모르면 null
 * @param rerankCalls 재정렬기 호출 수
 * @param keywordCandidates 키워드 후보 수
 * @param vectorCandidates 벡터 후보 수 (상위 K로 자르기 전, 공개 범위와 조건을 통과한 벡터 수)
 */
public record SearchUsage(
        String embeddingModel,
        int embeddingCalls,
        Long embeddingInputTokens,
        int rerankCalls,
        int keywordCandidates,
        int vectorCandidates) {

    public static SearchUsage keyword(int keywordCandidates) {
        return new SearchUsage(null, 0, null, 0, keywordCandidates, 0);
    }
}
