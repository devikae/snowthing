package com.ikae.snowthing.domain.search.semantic;

import java.util.List;

/**
 * 텍스트를 벡터로 바꾸는 제공자. 구현을 설정으로 고른다({@code snowthing.search.semantic.provider}).
 *
 * <p>의미 검색이 꺼져 있으면({@code snowthing.search.semantic.enabled=false}) 어떤 경로에서도 부르지 않는다. 외부 API 구현은 호출마다
 * 비용이 생길 수 있으므로 켜기 전에 사용자 승인이 필요하다.
 */
public interface EmbeddingProvider {

    /** 저장된 벡터를 구분하는 식별자. 모델이나 차원을 바꾸면 값도 바뀌어야 한다. */
    String modelId();

    int dimension();

    /**
     * 입력 순서대로 벡터를 돌려준다. 실패하면 {@link EmbeddingProviderException}을 던진다.
     *
     * @param texts 1개 이상
     */
    EmbeddingResult embed(List<String> texts);
}
