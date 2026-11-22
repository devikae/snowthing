package com.ikae.snowthing.domain.moderation.api;

/** 2단계 호출 실패. 메시지에 본문, 응답 원문, 키를 넣지 않는다. */
public class ModerationApiException extends RuntimeException {

    public ModerationApiException(String message) {
        super(message);
    }
}
