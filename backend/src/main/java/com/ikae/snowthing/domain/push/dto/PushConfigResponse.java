package com.ikae.snowthing.domain.push.dto;

/** 서버에 VAPID 키가 없으면 enabled=false, publicKey=null이다. 화면은 이때 휴대폰 알림 켜기를 숨긴다. */
public record PushConfigResponse(boolean enabled, String publicKey) {}
