package com.ikae.snowthing.domain.moderation.entity;

/** 판정. 뒤에 있을수록 무겁다. 여러 단계의 판정은 가장 무거운 쪽을 쓴다. */
public enum ModerationVerdict {
    PASS,
    REVIEW,
    HIDE;

    public ModerationVerdict max(ModerationVerdict other) {
        return other != null && other.ordinal() > ordinal() ? other : this;
    }
}
