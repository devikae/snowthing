package com.ikae.snowthing.domain.dmmail.entity;

/** 발송 직전 확인에서 제외한 이유. 판단 순서도 이 순서다. */
public enum DmMailSkipReason {
    /** 쪽지 생성 후 발송 기한이 지남 */
    EXPIRED,
    /** 받는 사람이 정지 또는 탈퇴 상태 */
    RECIPIENT_INACTIVE,
    /** 받는 사람이 알림을 끔(설정 행 없음 포함) */
    NOTIFICATION_OFF,
    /** 이메일 미인증 또는 받을 수 없는 내부 주소 */
    EMAIL_UNDELIVERABLE,
    /** 받는 사람이 이미 쪽지를 읽음 */
    ALREADY_READ,
    /** 쪽지 행이 없음 */
    MESSAGE_MISSING
}
