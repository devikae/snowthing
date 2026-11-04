package com.ikae.snowthing.domain.search.semantic;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 의미 검색 실험 설정 ({@code snowthing.search.semantic}). 기본은 꺼짐이다. 꺼져 있으면 임베딩 제공자를 한 번도 부르지 않고, 하이브리드 검색 요청은
 * SEARCH_002로 거절한다.
 *
 * @param enabled 의미 검색과 벡터 갱신을 켠다
 * @param provider {@code none}(기본), {@code hashing}(로컬 파이프라인 확인용, 비용 없음, 의미 모델 아님), {@code http}
 *     (OpenAI 호환 embeddings 엔드포인트, 호출마다 비용 가능)
 * @param rrfK RRF 상수 k. 0 이하면 60
 * @param candidateK 키워드와 벡터 각각에서 가져올 후보 수. 0 이하면 50, 최대 200
 * @param refreshBatchSize 벡터 갱신 한 번에 제공자로 보낼 구간 수. 0 이하면 32
 * @param refreshMaxBatches 폴링 한 번에 연속으로 보낼 최대 배치 수. 0 이하면 4
 */
@ConfigurationProperties(prefix = "snowthing.search.semantic")
public record SemanticSearchProperties(
        boolean enabled,
        String provider,
        int rrfK,
        int candidateK,
        int refreshBatchSize,
        int refreshMaxBatches,
        Http http) {

    static final int MAX_CANDIDATE_K = 200;

    public int effectiveRrfK() {
        return rrfK > 0 ? rrfK : ReciprocalRankFusion.DEFAULT_K;
    }

    public int effectiveCandidateK() {
        return candidateK > 0 ? Math.min(candidateK, MAX_CANDIDATE_K) : 50;
    }

    public int effectiveRefreshBatchSize() {
        return refreshBatchSize > 0 ? refreshBatchSize : 32;
    }

    public int effectiveRefreshMaxBatches() {
        return refreshMaxBatches > 0 ? refreshMaxBatches : 4;
    }

    public Http httpOrEmpty() {
        return http != null ? http : new Http(null, null, 0, null, 0, 0);
    }

    /**
     * OpenAI 호환 {@code POST {baseUrl}/embeddings}.
     *
     * @param apiKey 환경 변수 {@code SNOWTHING_SEARCH_EMBEDDING_API_KEY}로만 넣는다. 로그와 toString에 나오지 않는다
     */
    public record Http(
            String baseUrl,
            String model,
            int dimension,
            String apiKey,
            int connectTimeoutMillis,
            int readTimeoutMillis) {

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        public int effectiveConnectTimeoutMillis() {
            return connectTimeoutMillis > 0 ? connectTimeoutMillis : 2000;
        }

        public int effectiveReadTimeoutMillis() {
            return readTimeoutMillis > 0 ? readTimeoutMillis : 10000;
        }

        @Override
        public String toString() {
            return "Http[baseUrl="
                    + baseUrl
                    + ", model="
                    + model
                    + ", dimension="
                    + dimension
                    + ", apiKey="
                    + (hasApiKey() ? "****" : "")
                    + "]";
        }
    }
}
