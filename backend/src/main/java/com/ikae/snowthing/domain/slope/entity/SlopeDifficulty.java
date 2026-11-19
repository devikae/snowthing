package com.ikae.snowthing.domain.slope.entity;

/**
 * 색 표시용 난이도 묶음. 리조트마다 표기가 달라 공식 난이도는 따로 그대로 둔다.
 *
 * <p>추천이나 비교 기준으로 쓰지 않는다. 비슷한 슬로프는 경사 수치로만 찾는다.
 */
public enum SlopeDifficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}
