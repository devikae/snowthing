package com.ikae.snowthing.domain.contentreport.entity;

public enum ReportTargetType {
    POST,
    COMMENT,
    /** 받은 쪽지. 쪽지는 공개 대상이 아니라 처리 완료(DISMISS)만 받는다 (IKA-123). */
    MESSAGE
}
