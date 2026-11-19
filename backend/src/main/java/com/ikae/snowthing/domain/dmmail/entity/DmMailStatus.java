package com.ikae.snowthing.domain.dmmail.entity;

/**
 * 쪽지 메일 발송 상태.
 *
 * <ul>
 *   <li>PENDING: 대기. 선점 대상
 *   <li>PROCESSING: 워커가 선점함. claim_token과 lease_until이 소유권이다
 *   <li>ACCEPTED: SES가 요청을 수락함. 수신함 도착을 뜻하지 않는다
 *   <li>RETRY_WAIT: 요청이 나가지 않았음이 확실하거나 SES가 명시적으로 나중에 하라고 거절함. due_at 이후 다시 선점 대상
 *   <li>FAILED: 영구 거절이거나 재시도 한도를 다 씀
 *   <li>UNKNOWN: SES가 수락했는지 알 수 없음. 자동으로 다시 보내지 않고 운영자가 확인한다
 *   <li>SKIPPED: 발송 직전 확인에서 제외됨
 * </ul>
 */
public enum DmMailStatus {
    PENDING,
    PROCESSING,
    ACCEPTED,
    RETRY_WAIT,
    FAILED,
    UNKNOWN,
    SKIPPED
}
