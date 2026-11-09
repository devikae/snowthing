package com.ikae.snowthing.domain.slopestatus.source;

/** 출처 호출이나 파싱 실패. 메시지는 DB 실패 사유와 로그에 남으므로 URL이나 응답 원문을 넣지 않는다. */
public class SlopeFetchException extends RuntimeException {

    public SlopeFetchException(String message) {
        super(message);
    }
}
