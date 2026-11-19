package com.ikae.snowthing.domain.slope.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.slope.entity.Slope;
import com.ikae.snowthing.domain.slope.entity.SlopeDifficulty;
import com.ikae.snowthing.domain.slope.service.SimilarSlopeFinder.Match;
import com.ikae.snowthing.domain.slope.service.SimilarSlopeFinder.Metric;

class SimilarSlopeFinderTest {

    private static Slope slope(String name, Double avg, Double max, Integer length) {
        return Slope.builder()
                .name(name)
                .difficulty(SlopeDifficulty.INTERMEDIATE)
                .avgGradient(avg == null ? null : BigDecimal.valueOf(avg))
                .maxGradient(max == null ? null : BigDecimal.valueOf(max))
                .lengthM(length)
                .sourceUrl("https://example.invalid")
                .checkedAt(LocalDate.of(2026, 11, 20))
                .build();
    }

    @Test
    @DisplayName("두 슬로프가 모두 평균 경사를 가지면 평균끼리 비교한다")
    void comparesAverageWhenBothHaveIt() {
        Slope base = slope("기준", 10.0, 30.0, 1000);
        Slope candidate = slope("후보", 11.0, 12.0, 1000);

        List<Match> result = SimilarSlopeFinder.find(base, List.of(candidate), 5);

        assertThat(result)
                .singleElement()
                .satisfies(
                        match -> {
                            assertThat(match.metric()).isEqualTo(Metric.AVG);
                            assertThat(match.gradientDiff()).isEqualByComparingTo("1.0");
                        });
    }

    @Test
    @DisplayName("평균이 한쪽에만 있으면 평균과 최대를 섞지 않고 최대끼리 비교한다")
    void fallsBackToMaxWithoutMixingMetrics() {
        Slope base = slope("기준", 10.0, 25.0, null);
        Slope maxOnly = slope("최대만", null, 24.0, null);
        // 평균 10과 최대 10을 섞어 비교하면 차이 0이 되지만, 같은 지표가 아니므로 최대 25와 10을 비교해 빠져야 한다.
        Slope trap = slope("섞이면 안 됨", null, 10.0, null);

        List<Match> result = SimilarSlopeFinder.find(base, List.of(maxOnly, trap), 5);

        assertThat(result).extracting(match -> match.slope().getName()).containsExactly("최대만");
        assertThat(result.get(0).metric()).isEqualTo(Metric.MAX);
    }

    @Test
    @DisplayName("함께 가진 경사 지표가 없으면 후보에서 뺀다")
    void skipsWhenNoCommonMetric() {
        Slope base = slope("기준", 10.0, null, 800);
        Slope candidate = slope("최대만", null, 10.0, 800);
        Slope empty = slope("경사 없음", null, null, 800);

        assertThat(SimilarSlopeFinder.find(base, List.of(candidate, empty), 5)).isEmpty();
    }

    @Test
    @DisplayName("경사 차이가 3도를 넘으면 비슷하다고 보지 않는다")
    void dropsCandidatesBeyondThreshold() {
        Slope base = slope("기준", 10.0, null, null);

        List<Match> result =
                SimilarSlopeFinder.find(
                        base,
                        List.of(
                                slope("3도 차이", 13.0, null, null),
                                slope("3.1도 차이", 13.1, null, null)),
                        5);

        assertThat(result).extracting(match -> match.slope().getName()).containsExactly("3도 차이");
    }

    @Test
    @DisplayName("경사 차이가 작은 순, 같으면 길이 차이가 작은 순, 길이를 모르면 뒤로 정렬하고 개수를 자른다")
    void ordersByGradientThenLength() {
        Slope base = slope("기준", 10.0, null, 1000);
        List<Slope> candidates =
                List.of(
                        slope("차이 1, 길이 모름", 11.0, null, null),
                        slope("차이 1, 길이 차이 500", 9.0, null, 1500),
                        slope("차이 1, 길이 차이 100", 11.0, null, 1100),
                        slope("차이 0.5", 10.5, null, 3000),
                        slope("차이 2", 12.0, null, 1000));

        List<Match> result = SimilarSlopeFinder.find(base, candidates, 4);

        assertThat(result)
                .extracting(match -> match.slope().getName())
                .containsExactly("차이 0.5", "차이 1, 길이 차이 100", "차이 1, 길이 차이 500", "차이 1, 길이 모름");
        assertThat(result.get(1).lengthDiff()).isEqualTo(100);
        assertThat(result.get(3).lengthDiff()).isNull();
    }
}
