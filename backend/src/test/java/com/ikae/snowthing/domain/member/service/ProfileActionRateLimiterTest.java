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
    void globalBudgetDenialHasSeparateErrorCode() {
        var shared = mock(com.ikae.snowthing.global.security.SharedRequestLimiter.class);
        var limiter = new ProfileActionRateLimiter(Clock.systemUTC(), shared);
        when(shared.acquire(any()))
                .thenReturn(
                        new com.ikae.snowthing.global.security.SharedRequestLimiter.Decision(
                                true, 1800, "snowthing:limits:images:global:hour"));
        assertThatThrownBy(
                        () -> limiter.check("account", ProfileActionRateLimiter.Action.POST_IMAGE))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMAGE_GLOBAL_LIMIT);
    }

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
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThatCode(() -> limiter.check("first", ProfileActionRateLimiter.Action.NICKNAME))
                .doesNotThrowAnyException();
    }

    @Test
    void photoUsesFivePerDaySharedBudgetAndFailsClosed() {
        var shared = mock(com.ikae.snowthing.global.security.SharedRequestLimiter.class);
        var limiter = new ProfileActionRateLimiter(Clock.systemUTC(), shared);
        when(shared.acquire(any()))
                .thenReturn(
                        new com.ikae.snowthing.global.security.SharedRequestLimiter.Decision(
                                true, 0));
        limiter.check("account", ProfileActionRateLimiter.Action.PHOTO);
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(shared).acquire(captor.capture());
        var budget =
                (com.ikae.snowthing.global.security.SharedRequestLimiter.Limit)
                        captor.getValue().get(0);
        assertThat(budget.count()).isEqualTo(5);
        assertThat(budget.duration()).isEqualTo(java.time.Duration.ofDays(1));
        assertThat(budget.key()).startsWith("snowthing:limits:photo:");
        when(shared.acquire(any()))
                .thenReturn(
                        new com.ikae.snowthing.global.security.SharedRequestLimiter.Decision(
                                false, 60));
        assertThatThrownBy(() -> limiter.check("account", ProfileActionRateLimiter.Action.PHOTO))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROFILE_LIMIT_UNAVAILABLE);
    }

    @Test
    void postImagesUseThirtyPerHourSharedBudget() {
        var shared = mock(com.ikae.snowthing.global.security.SharedRequestLimiter.class);
        var limiter = new ProfileActionRateLimiter(Clock.systemUTC(), shared);
        when(shared.acquire(any()))
                .thenReturn(
                        new com.ikae.snowthing.global.security.SharedRequestLimiter.Decision(
                                true, 0));
        limiter.check("account", ProfileActionRateLimiter.Action.POST_IMAGE);
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(java.util.List.class);
        verify(shared).acquire(captor.capture());
        var budget =
                (com.ikae.snowthing.global.security.SharedRequestLimiter.Limit)
                        captor.getValue().get(0);
        assertThat(budget.count()).isEqualTo(30);
        assertThat(budget.duration()).isEqualTo(java.time.Duration.ofHours(1));
        assertThat(budget.key()).startsWith("snowthing:limits:post_image:");
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
