package com.ikae.snowthing.domain.search.semantic;

import java.util.List;

/**
 * @param vectors 입력 순서대로의 벡터
 * @param inputTokens 제공자가 알려 준 입력 토큰 수. 모르면 null (비용 추정용 메타데이터)
 */
public record EmbeddingResult(List<float[]> vectors, Long inputTokens) {

    public EmbeddingResult {
        vectors = vectors == null ? List.of() : List.copyOf(vectors);
    }
}
