package com.ikae.snowthing.domain.search.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.search.dto.SearchMode;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class SemanticSearchUnitTest {

    @Test
    @DisplayName("벡터는 float32 little-endian으로 저장했다가 그대로 읽힌다")
    void vectorBytesRoundTrip() {
        float[] vector = {1.5f, -0.25f, 0f, Float.MIN_VALUE};

        byte[] bytes = VectorMath.toBytes(vector);

        assertThat(bytes).hasSize(16);
        assertThat(bytes[0]).isEqualTo((byte) 0x00);
        assertThat(bytes[3]).isEqualTo((byte) 0x3f); // 1.5f = 0x3FC00000, little-endian
        assertThat(VectorMath.fromBytes(bytes)).containsExactly(vector);
        assertThatThrownBy(() -> VectorMath.fromBytes(new byte[3]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("코사인 유사도: 같은 방향 1, 반대 -1, 직교 0, 0 벡터와 길이가 다른 벡터는 0")
    void cosine() {
        assertThat(VectorMath.cosine(new float[] {1, 2}, new float[] {2, 4}))
                .isCloseTo(1.0, within());
        assertThat(VectorMath.cosine(new float[] {1, 0}, new float[] {-1, 0}))
                .isCloseTo(-1.0, within());
        assertThat(VectorMath.cosine(new float[] {1, 0}, new float[] {0, 1})).isZero();
        assertThat(VectorMath.cosine(new float[] {0, 0}, new float[] {1, 1})).isZero();
        assertThat(VectorMath.cosine(new float[] {1}, new float[] {1, 1})).isZero();
    }

    @Test
    @DisplayName("해시 임베딩은 결정적이고 길이가 1이며, 글자가 겹치는 문장이 더 가깝다")
    void hashingProviderIsDeterministic() {
        HashingEmbeddingProvider provider = new HashingEmbeddingProvider(128);

        float[] a = provider.embed(List.of("바인딩 각도 추천")).vectors().getFirst();
        float[] again = provider.embed(List.of("바인딩  각도\n추천")).vectors().getFirst();
        float[] near = provider.embed(List.of("바인딩 각도는 몇 도가 좋나요")).vectors().getFirst();
        float[] far = provider.embed(List.of("스키장 셔틀버스 시간표")).vectors().getFirst();

        assertThat(again).containsExactly(a);
        assertThat(VectorMath.cosine(a, a)).isCloseTo(1.0, within());
        assertThat(VectorMath.cosine(a, near)).isGreaterThan(VectorMath.cosine(a, far));
        assertThat(provider.embed(List.of("")).vectors().getFirst()).containsOnly(0f);
        assertThat(provider.modelId()).isEqualTo("hashing-bigram-v1-d128");
    }

    @Test
    @DisplayName("검색 방식은 비어 있으면 keyword, 대소문자 무시, 모르는 값은 SEARCH_001")
    void parsesSearchMode() {
        assertThat(SearchMode.parse(null)).isEqualTo(SearchMode.KEYWORD);
        assertThat(SearchMode.parse(" ")).isEqualTo(SearchMode.KEYWORD);
        assertThat(SearchMode.parse("HYBRID")).isEqualTo(SearchMode.HYBRID);
        assertThat(SearchMode.parse(" hybrid_rerank ")).isEqualTo(SearchMode.HYBRID_RERANK);
        assertThat(SearchMode.HYBRID_RERANK.value()).isEqualTo("hybrid_rerank");
        assertThatThrownBy(() -> SearchMode.parse("vector"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEARCH_INVALID_REQUEST);
    }

    @Test
    @DisplayName("설정 기본값과 범위, API 키는 toString에 나오지 않는다")
    void propertiesDefaultsAndMasking() {
        SemanticSearchProperties.Http http =
                new SemanticSearchProperties.Http(
                        "https://example.invalid/v1", "m", 8, "sk-secret-value", 0, 0);
        SemanticSearchProperties properties =
                new SemanticSearchProperties(false, "none", 0, 999, 0, 0, http);

        assertThat(properties.effectiveRrfK()).isEqualTo(60);
        assertThat(properties.effectiveCandidateK()).isEqualTo(200);
        assertThat(properties.effectiveRefreshBatchSize()).isEqualTo(32);
        assertThat(properties.toString()).doesNotContain("sk-secret-value").contains("****");
        assertThat(http.effectiveReadTimeoutMillis()).isEqualTo(10000);
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(1e-6);
    }
}
