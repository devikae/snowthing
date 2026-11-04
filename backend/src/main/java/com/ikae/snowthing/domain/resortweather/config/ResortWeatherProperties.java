package com.ikae.snowthing.domain.resortweather.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snowthing.weather")
public record ResortWeatherProperties(Kma kma, int connectTimeoutMillis, int readTimeoutMillis) {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 1000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 2000;

    /** 공공데이터포털 단기예보 조회서비스 일반 인증키(Decoding). 비어 있으면 기상청 소스를 쓰지 않는다. */
    public record Kma(String serviceKey) {}

    public String kmaServiceKey() {
        return kma == null ? null : kma.serviceKey();
    }

    public int effectiveConnectTimeoutMillis() {
        return connectTimeoutMillis > 0 ? connectTimeoutMillis : DEFAULT_CONNECT_TIMEOUT_MILLIS;
    }

    public int effectiveReadTimeoutMillis() {
        return readTimeoutMillis > 0 ? readTimeoutMillis : DEFAULT_READ_TIMEOUT_MILLIS;
    }
}
