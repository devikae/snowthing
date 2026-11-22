package com.ikae.snowthing.domain.moderation.entity;

/** 검사 대상 종류. 문자열로 저장하므로 이름을 바꾸지 않는다. 장터와 카풀은 글 번호를 대상 번호로 쓴다. */
public enum ModerationTargetType {
    POST,
    COMMENT,
    MARKET,
    CARPOOL,
    RESORT_REPORT;

    /** post 표의 글인지. */
    public boolean isPost() {
        return this == POST || this == MARKET || this == CARPOOL;
    }
}
