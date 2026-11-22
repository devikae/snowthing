package com.ikae.snowthing.domain.dmmail.entity;

/**
 * 발송에서 제외한 이유. 발송 직전 확인의 판단 순서는 EXPIRED부터 MESSAGE_MISSING까지 선언 순서와 같다. SEND_OFF_EXPIRED는 발송 직전 확인이
 * 아니라 스케줄러의 일괄 정리가 쓴다.
 */
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
    MESSAGE_MISSING,
    /** 발송이 꺼져 있던 동안 발송 기한이 지남. 발송을 다시 켤 때 밀린 메일이 한꺼번에 나가지 않게 정리한 행 (IKA-109) */
    SEND_OFF_EXPIRED
}
