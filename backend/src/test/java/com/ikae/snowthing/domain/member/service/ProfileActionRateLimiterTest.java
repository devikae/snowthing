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
    void nicknameAttemptsAreBoundedPerAccountAndExpire() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-11-01T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        var limiter =
                new ProfileActionRateLimiter(
                        clock, mock(com.ikae.snowthing.global.security.SharedRequestLimiter.class));
        for (int i = 0; i < 30; i++)
            limiter.check("first", ProfileActionRateLimiter.Action.NICKNAME);
        assertThatThrownBy(() -> limiter.check("first", ProfileActionRateLimiter.Action.NICKNAME))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROFILE_ACTION_LIMIT);
        assertThatCode(() -> limiter.check("second", ProfileActionRateLimiter.Action.NICKNAME))
                .doesNotThrowAnyException();
        assertThatCode(() -> limiter.check("first", ProfileActionRateLimiter.Action.PHOTO))
                .doesNotThrowAnyException();
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThatCode(() -> limiter.check("first", ProfileActionRateLimiter.Action.NICKNAME))
                .doesNotThrowAnyException();
    }

    @Test
    void passwordChecksUseSharedBudgetAndFailClosedDuringStoreFailure() {
        var shared = mock(com.ikae.snowthing.global.security.SharedRequestLimiter.class);
        var limiter = new ProfileActionRateLimiter(Clock.systemUTC(), shared);
        when(shared.acquire(any()))
                .thenReturn(
                        new com.ikae.snowthing.global.security.SharedRequestLimiter.Decision(
                                true, 0));
        limiter.check("account", ProfileActionRateLimiter.Action.PASSWORD);
        when(shared.acquire(any()))
                .thenReturn(
                        new com.ikae.snowthing.global.security.SharedRequestLimiter.Decision(
                                true, 900));
        assertThatThrownBy(() -> limiter.check("account", ProfileActionRateLimiter.Action.PASSWORD))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROFILE_ACTION_LIMIT);
        when(shared.acquire(any()))
                .thenReturn(
                        new com.ikae.snowthing.global.security.SharedRequestLimiter.Decision(
                                false, 60));
        assertThatThrownBy(() -> limiter.check("account", ProfileActionRateLimiter.Action.PASSWORD))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROFILE_LIMIT_UNAVAILABLE);
    }
}
