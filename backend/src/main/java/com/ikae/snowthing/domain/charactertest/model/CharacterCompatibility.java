package com.ikae.snowthing.domain.charactertest.model;

import java.util.List;

/**
 * 두 사람의 궁합 점수(16~100). 궁합표 16~60, 타는 습관 0~30, 유형 답변 일치 0~10을 더한다.
 *
 * <p>습관 차이가 등급 경계를 넘을 수 있어서, 화면에 부르는 단계는 궁합표 등급이 아니라 최종 점수로 정한다.
 */
public record CharacterCompatibility(int score, CompatibilityLevel level, List<Integer> habitGaps) {

    private static final int MATCH_WEIGHT = 10;
    private static final int[] HABIT_POINTS_BY_GAP = {10, 6, 0};

    public CharacterCompatibility {
        habitGaps = List.copyOf(habitGaps);
    }

    public static CharacterCompatibility of(
            CompatibilityLevel tableLevel, CharacterAnswers mine, CharacterAnswers other) {
        List<Integer> gaps = mine.habitGaps(other);
        int habitScore = gaps.stream().mapToInt(gap -> HABIT_POINTS_BY_GAP[gap]).sum();
        int matchScore =
                Math.round(
                        (float) mine.matchCount(other)
                                * MATCH_WEIGHT
                                / CharacterAnswers.TYPE_QUESTION_COUNT);
        int score = tableLevel.baseScore() + habitScore + matchScore;
        return new CharacterCompatibility(score, CompatibilityLevel.ofScore(score), gaps);
    }
}
