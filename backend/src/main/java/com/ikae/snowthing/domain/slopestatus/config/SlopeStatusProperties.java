package com.ikae.snowthing.domain.slopestatus.config;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 슬로프 현황 수집 설정.
 *
 * @param enabledResorts 수집할 리조트 코드. 기본은 비어 있어 아무 곳도 수집하지 않는다. 운영에서 사용자가 리조트별로 켠다.
 */
@ConfigurationProperties(prefix = "snowthing.slope-status")
public record SlopeStatusProperties(
        List<String> enabledResorts, int connectTimeoutMillis, int readTimeoutMillis) {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 3000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;

    public Set<String> enabledResortCodes() {
        if (enabledResorts == null) {
            return Set.of();
        }
        return enabledResorts.stream()
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public int effectiveConnectTimeoutMillis() {
        return connectTimeoutMillis > 0 ? connectTimeoutMillis : DEFAULT_CONNECT_TIMEOUT_MILLIS;
    }

    public int effectiveReadTimeoutMillis() {
        return readTimeoutMillis > 0 ? readTimeoutMillis : DEFAULT_READ_TIMEOUT_MILLIS;
    }
}
