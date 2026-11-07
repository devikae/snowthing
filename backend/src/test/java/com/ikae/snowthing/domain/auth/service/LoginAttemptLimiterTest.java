package com.ikae.snowthing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.SharedRequestLimiter;

class LoginAttemptLimiterTest {
    private final SharedRequestLimiter shared = mock(SharedRequestLimiter.class);
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(shared, new EmailNormalizer(), true);

    @Test
    @DisplayName("집계 저장소를 쓸 수 없으면 비밀번호를 검사하지 않고 503으로 거부한다")
    void rejectsWhenStoreIsUnavailable() {
        when(shared.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(false, 60));

        assertThatThrownBy(() -> limiter.acquire("user@example.com", "203.0.113.10"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.CONTENT_WRITE_LIMIT_UNAVAILABLE));
    }

    @Test
    @DisplayName("성공 뒤 횟수 정리가 실패해도 로그인은 막지 않는다")
    void cleanupFailureDoesNotBreakLogin() {
        doThrow(new DataAccessResourceFailureException("down")).when(shared).reset(anyString());

        assertThatCode(() -> limiter.succeeded("user@example.com", "203.0.113.10"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("꺼져 있으면 집계 저장소를 쓰지 않는다")
    void disabledLimiterSkipsStore() {
        LoginAttemptLimiter disabled =
                new LoginAttemptLimiter(shared, new EmailNormalizer(), false);

        disabled.acquire("user@example.com", "203.0.113.10");
        disabled.succeeded("user@example.com", "203.0.113.10");

        verifyNoInteractions(shared);
    }
}
