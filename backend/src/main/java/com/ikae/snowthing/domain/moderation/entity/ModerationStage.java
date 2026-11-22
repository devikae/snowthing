package com.ikae.snowthing.domain.moderation.entity;

/** 판정한 단계. LLM은 2차 출시용으로 자리만 둔다. */
public enum ModerationStage {
    RULE,
    MODERATION_API,
    LLM
}
