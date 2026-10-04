package com.ikae.snowthing.domain.carpool.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snowthing.carpool.external")
public record CarpoolExternalApiProperties(
        Kakao kakao, Opinet opinet, int connectTimeoutMillis, int readTimeoutMillis) {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 4000;

    public record Kakao(String restApiKey) {}

    public record Opinet(String apiKey) {}

    public boolean hasKakaoKey() {
        return kakao != null && kakao.restApiKey() != null && !kakao.restApiKey().isBlank();
    }

    public boolean hasOpinetKey() {
        return opinet != null && opinet.apiKey() != null && !opinet.apiKey().isBlank();
    }

    public int effectiveConnectTimeoutMillis() {
        return connectTimeoutMillis > 0 ? connectTimeoutMillis : DEFAULT_CONNECT_TIMEOUT_MILLIS;
    }

    public int effectiveReadTimeoutMillis() {
        return readTimeoutMillis > 0 ? readTimeoutMillis : DEFAULT_READ_TIMEOUT_MILLIS;
    }
}
