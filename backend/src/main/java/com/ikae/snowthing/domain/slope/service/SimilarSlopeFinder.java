package com.ikae.snowthing.domain.slope.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.ikae.snowthing.domain.slope.entity.Slope;

/**
 * 경사가 비슷한 슬로프를 찾는다. 외부 의존이 없는 순수 계산이다.
 *
 * <p>평균 경사는 코스 전체의 기울기, 최대 경사는 가장 가파른 짧은 구간의 기울기다. 둘을 섞으면 틀린 매칭이 되므로 두 슬로프가 함께 가진 지표끼리만 비교한다. 평균이 둘
 * 다 있으면 평균, 아니면 최대, 공통 지표가 없으면 후보에서 뺀다.
 *
 * <p>경사 차이가 작은 순서로, 같으면 길이 차이가 작은 순서로 정렬한다. 차이가 너무 크면 "비슷하다"고 말할 수 없으므로 {@link
 * #MAX_GRADIENT_DIFF}도를 넘는 후보는 버린다.
 */
public final class SimilarSlopeFinder {

    /** 이보다 경사 차이가 크면 비슷한 슬로프로 보여 주지 않는다. */
    public static final BigDecimal MAX_GRADIENT_DIFF = new BigDecimal("3.0");

    public enum Metric {
        AVG,
        MAX
    }

    /** 후보 한 건과 비교 근거. lengthDiff는 두 길이 중 하나라도 없으면 null이다. */
    public record Match(
            Slope slope,
            Metric metric,
            BigDecimal baseGradient,
            BigDecimal gradient,
            BigDecimal gradientDiff,
            Integer lengthDiff) {}

    private SimilarSlopeFinder() {}

    public static List<Match> find(Slope base, List<Slope> candidates, int limit) {
        return candidates.stream()
                .filter(candidate -> candidate != base)
                .filter(candidate -> !sameId(base, candidate))
                .map(candidate -> match(base, candidate))
                .filter(Objects::nonNull)
                .filter(match -> match.gradientDiff().compareTo(MAX_GRADIENT_DIFF) <= 0)
                .sorted(
                        Comparator.comparing(Match::gradientDiff)
                                .thenComparing(
                                        Match::lengthDiff,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(
                                        match -> match.slope().getId(),
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();
    }

    static Match match(Slope base, Slope candidate) {
        Metric metric;
        BigDecimal baseGradient;
        BigDecimal gradient;
        if (base.getAvgGradient() != null && candidate.getAvgGradient() != null) {
            metric = Metric.AVG;
            baseGradient = base.getAvgGradient();
            gradient = candidate.getAvgGradient();
        } else if (base.getMaxGradient() != null && candidate.getMaxGradient() != null) {
            metric = Metric.MAX;
            baseGradient = base.getMaxGradient();
            gradient = candidate.getMaxGradient();
        } else {
            return null;
        }
        Integer lengthDiff =
                base.getLengthM() == null || candidate.getLengthM() == null
                        ? null
                        : Math.abs(base.getLengthM() - candidate.getLengthM());
        return new Match(
                candidate,
                metric,
                baseGradient,
                gradient,
                baseGradient.subtract(gradient).abs(),
                lengthDiff);
    }

    private static boolean sameId(Slope base, Slope candidate) {
        return base.getId() != null && base.getId().equals(candidate.getId());
    }
}
