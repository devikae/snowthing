package com.ikae.snowthing.domain.resortweather.source;

/** 외부 소스 호출이나 파싱 실패. 메시지에 요청 URL(서비스 키 포함 가능)을 넣지 않는다. */
public class WeatherFetchException extends RuntimeException {

    public WeatherFetchException(String message) {
        super(message);
    }
}
