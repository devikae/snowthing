package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class ProfileActionRateLimiterTest {
    @Test
    void rejectedPasswordAttemptsAreBoundedPerAccountAndExpire() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-11-01T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        var limiter = new ProfileActionRateLimiter(clock);
        for (int i = 0; i < 10; i++)
            limiter.check("first", ProfileActionRateLimiter.Action.PASSWORD);
        assertThatThrownBy(() -> limiter.check("first", ProfileActionRateLimiter.Action.PASSWORD))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROFILE_ACTION_LIMIT);
        assertThatCode(() -> limiter.check("second", ProfileActionRateLimiter.Action.PASSWORD))
                .doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("first", ProfileActionRateLimiter.Action.NICKNAME))
                .doesNotThrowAnyException();
        when(clock.instant()).thenReturn(start.plusSeconds(900));
        assertThatCode(() -> limiter.check("first", ProfileActionRateLimiter.Action.PASSWORD))
                .doesNotThrowAnyException();
    }
}
