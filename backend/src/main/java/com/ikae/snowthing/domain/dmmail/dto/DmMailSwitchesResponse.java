package com.ikae.snowthing.domain.dmmail.dto;

/**
 * 쪽지 메일 스위치 상태 (IKA-109). 스위치를 바꾸는 것은 {@code PATCH /api/v1/admin/configs/{key}}로 한다.
 *
 * @param enqueue 발송 요청 기록
 * @param send 메일 발송
 * @param mailProviderReady 메일 공급자가 ses로 켜져 있는지. false면 발송 스위치를 켜도 보내지 않는다
 * @param deadlineHours 쪽지가 생긴 뒤 이 시간이 지나면 보내지 않는다
 */
public record DmMailSwitchesResponse(
        SwitchState enqueue, SwitchState send, boolean mailProviderReady, long deadlineHours) {

    /**
     * @param configKey 관리자 설정 키
     * @param serverAllowed 서버 설정(환경변수)이 허용하는지. false면 화면 스위치를 켜도 동작하지 않는다
     * @param switchOn 관리자 화면 스위치 값
     * @param effective 지금 실제로 동작하는지
     */
    public record SwitchState(
            String configKey, boolean serverAllowed, boolean switchOn, boolean effective) {}
}
