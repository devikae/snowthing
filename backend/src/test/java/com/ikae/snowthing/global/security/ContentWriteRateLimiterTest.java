package com.ikae.snowthing.global.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class ContentWriteRateLimiterTest {
    private final SharedRequestLimiter shared = mock(SharedRequestLimiter.class);
    private final ContentWriteRateLimiter limiter = new ContentWriteRateLimiter(shared, true);

    @Test
    void deniedWindowReturnsWriteLimit() {
        when(shared.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(true, 3));

        assertThatThrownBy(
                        () ->
                                limiter.check(
                                        null, "1.2.3.4", ContentWriteRateLimiter.Action.COMMENT))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_WRITE_LIMIT);
    }

    @Test
    void unavailableBudgetStoreReturnsServiceUnavailable() {
        when(shared.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(false, 60));

        assertThatThrownBy(
                        () -> limiter.check(null, "1.2.3.4", ContentWriteRateLimiter.Action.POST))
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONTENT_WRITE_LIMIT_UNAVAILABLE);
    }

    @Test
    @SuppressWarnings("unchecked")
    void eachActionAndActorGetsSeparateBurstAndHourlyWindows() {
        when(shared.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(true, 0));
        ArgumentCaptor<List<SharedRequestLimiter.Limit>> captor =
                ArgumentCaptor.forClass(List.class);

        limiter.check(null, "1.2.3.4", ContentWriteRateLimiter.Action.COMMENT);
        limiter.check(null, "1.2.3.4", ContentWriteRateLimiter.Action.POST);
        limiter.check(null, "5.6.7.8", ContentWriteRateLimiter.Action.COMMENT);

        verify(shared, times(3)).acquire(captor.capture());
        var calls = captor.getAllValues();
        assertThat(calls.get(0))
                .extracting(SharedRequestLimiter.Limit::count)
                .containsExactly(1, 60);
        assertThat(calls.get(0).get(0).key()).startsWith("snowthing:limits:write:comment:");
        assertThat(calls.get(1).get(0).key()).startsWith("snowthing:limits:write:post:");
        assertThat(calls.get(2).get(0).key()).isNotEqualTo(calls.get(0).get(0).key());
        assertThat(calls)
                .flatExtracting(limits -> limits)
                .allSatisfy(limit -> assertThat(limit.key()).hasSizeLessThanOrEqualTo(100));
    }

    @Test
    void disabledLimiterSkipsBudgetStore() {
        new ContentWriteRateLimiter(shared, false)
                .check(null, "1.2.3.4", ContentWriteRateLimiter.Action.COMMENT);

        verifyNoInteractions(shared);
    }
}
