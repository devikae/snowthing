package com.ikae.snowthing.domain.charactertest.model;

/**
 * 안쪽 유형 16개. 서버 안에서만 쓴다. DTO에 이 타입이나 코드 문자열을 넣지 않는다(설계의 사용자 결정 1번).
 *
 * <p>순서는 궁합표를 찾을 때의 결정적 순서로도 쓰인다.
 */
public enum CharacterType {
    ESTJ,
    ESTP,
    ESFJ,
    ESFP,
    ENTJ,
    ENTP,
    ENFJ,
    ENFP,
    ISTJ,
    ISTP,
    ISFJ,
    ISFP,
    INTJ,
    INTP,
    INFJ,
    INFP;

    public CharacterJob job() {
        return CharacterJob.ofTypeCode(name());
    }

    public PlanStyle planStyle() {
        return name().endsWith("J") ? PlanStyle.PLANNED : PlanStyle.SPONTANEOUS;
    }
}
