package com.ikae.snowthing.domain.charactertest.model;

import java.util.Arrays;
import java.util.Optional;

/**
 * 궁합 5단계. 선언 순서가 지도의 위에서 아래 순서다.
 *
 * <p>baseScore는 궁합표 등급이 점수에 주는 몫이고, minScore는 최종 점수를 다시 단계 이름으로 부를 때의 하한이다.
 */
public enum CompatibilityLevel {
    BEST("찰떡", 60, 78),
    GOOD("같이 타요", 50, 68),
    NEUTRAL("무난", 40, 57),
    BICKER("티격태격", 28, 45),
    APART("각자 타요", 16, 0);

    private final String label;
    private final int baseScore;
    private final int minScore;

    CompatibilityLevel(String label, int baseScore, int minScore) {
        this.label = label;
        this.baseScore = baseScore;
        this.minScore = minScore;
    }

    public int baseScore() {
        return baseScore;
    }

    public static CompatibilityLevel ofScore(int score) {
        return Arrays.stream(values())
                .filter(level -> score >= level.minScore)
                .findFirst()
                .orElse(APART);
    }

    public static Optional<CompatibilityLevel> ofLabel(String label) {
        return Arrays.stream(values()).filter(level -> level.label.equals(label)).findFirst();
    }
}
