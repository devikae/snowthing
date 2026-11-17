package com.ikae.snowthing.domain.notification.dto;

/**
 * 알림 설정.
 *
 * @param dmEmailEnabled 쪽지 도착 메일 알림을 켰는지
 * @param emailDeliverable 메일을 받을 수 있는 주소가 있는지. 이메일이 인증되지 않았거나 소셜 가입의 내부 대체 주소면 false다. false면 켜도
 *     메일이 가지 않는다.
 */
public record NotificationSettingResponse(boolean dmEmailEnabled, boolean emailDeliverable) {}
