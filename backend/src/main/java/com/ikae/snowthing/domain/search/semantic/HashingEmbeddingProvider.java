package com.ikae.snowthing.domain.search.semantic;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 외부 호출 없는 결정적 임베딩. 공백을 뺀 글자 2-gram을 해시해 고정 차원에 더하고 길이를 1로 맞춘다.
 *
 * <p>의미 모델이 아니다. 글자 겹침만 보므로 키워드 검색과 비슷한 신호를 낸다. 저장, 갱신, RRF 결합, 평가 실행기가 끝까지 도는지 비용 없이 확인하는 데만 쓴다. 이
 * 제공자로 잰 Recall은 의미 검색 품질 결과로 쓰지 않는다.
 */
public class HashingEmbeddingProvider implements EmbeddingProvider {

    public static final int DEFAULT_DIMENSION = 256;

    private final int dimension;

    public HashingEmbeddingProvider() {
        this(DEFAULT_DIMENSION);
    }

    public HashingEmbeddingProvider(int dimension) {
        if (dimension < 1) {
            throw new IllegalArgumentException("dimension must be >= 1");
        }
        this.dimension = dimension;
    }

    @Override
    public String modelId() {
        return "hashing-bigram-v1-d" + dimension;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public EmbeddingResult embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(vector(text));
        }
        return new EmbeddingResult(vectors, null);
    }

    float[] vector(String text) {
        float[] vector = new float[dimension];
        String normalized =
                Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC)
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", "");
        int[] codePoints = normalized.codePoints().toArray();
        for (int i = 0; i + 1 < codePoints.length; i++) {
            int hash = 31 * codePoints[i] + codePoints[i + 1];
            hash ^= hash >>> 16;
            hash *= 0x45d9f3b;
            hash ^= hash >>> 16;
            int bucket = Math.floorMod(hash, dimension);
            vector[bucket] += (hash & 0x40000000) == 0 ? 1f : -1f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        if (norm > 0) {
            float scale = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < vector.length; i++) {
                vector[i] *= scale;
            }
        }
        return vector;
    }
}
