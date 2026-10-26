package com.ikae.snowthing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class OAuthAuthorizationIpRateLimiterTest {

    @Test
    @DisplayName("동일 IP의 분당 OAuth 인가 요청은 20회까지 허용하고 이후 요청을 차단한다")
    void check_SameIpExceedsLimit_ThrowsCustomException() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneOffset.UTC);
        OAuthAuthorizationIpRateLimiter limiter = new OAuthAuthorizationIpRateLimiter(clock);

        for (int request = 0;
                request < OAuthAuthorizationIpRateLimiter.MAX_REQUESTS_PER_IP;
                request++) {
            assertThatCode(() -> limiter.check("203.0.113.10")).doesNotThrowAnyException();
        }

        assertThatThrownBy(() -> limiter.check("203.0.113.10"))
                .isInstanceOf(CustomException.class)
                .extracting(exception -> ((CustomException) exception).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_RATE_LIMIT);
        assertThatCode(() -> limiter.check("203.0.113.11")).doesNotThrowAnyException();
    }
}
