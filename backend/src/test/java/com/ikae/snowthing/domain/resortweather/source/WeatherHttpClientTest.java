package com.ikae.snowthing.domain.resortweather.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class WeatherHttpClientTest {

    @Test
    @DisplayName("실패 이유에 상태 코드나 감싼 원인 종류를 남기고 메시지는 옮기지 않는다")
    void reasonKeepsKindWithoutMessage() {
        assertThat(
                        WeatherHttpClient.reason(
                                HttpServerErrorException.create(
                                        HttpStatus.BAD_GATEWAY, "secret", null, null, null)))
                .isEqualTo("HTTP 502");
        assertThat(
                        WeatherHttpClient.reason(
                                new ResourceAccessException(
                                        "serviceKey=secret", new HttpTimeoutException("x"))))
                .isEqualTo("ResourceAccessException(HttpTimeoutException)");
        assertThat(WeatherHttpClient.reason(new IllegalStateException("secret")))
                .isEqualTo("IllegalStateException");
    }
}
