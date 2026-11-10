package com.ikae.snowthing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.SharedRequestLimiter;

class LoginAttemptLimiterTest {
    private static final String ACCOUNT_PREFIX = "snowthing:limits:login:account:";

    private final SharedRequestLimiter shared = mock(SharedRequestLimiter.class);
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(
                    shared, new EmailNormalizer(), true, LoginAttemptLimiter.AccountLimits.DEFAULT);

    @Test
    @DisplayName("집계 저장소를 쓸 수 없으면 비밀번호를 검사하지 않고 503으로 거부한다")
    void rejectsWhenStoreIsUnavailable() {
        when(shared.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(false, 60));

        assertThatThrownBy(() -> limiter.acquire("user@example.com", "203.0.113.10"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.CONTENT_WRITE_LIMIT_UNAVAILABLE));
    }

    @Test
    @DisplayName("계정 상한은 IP와 무관한 고정 창 두 단계(15분 10회, 1시간 30회)로 함께 센다")
    void accountLimitsAreIndependentOfIp() {
        when(shared.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(true, 0));

        limiter.acquire("user@example.com", "203.0.113.10");
        limiter.acquire("USER@example.com ", "198.51.100.20");

        List<SharedRequestLimiter.Limit> first = captured(0);
        List<SharedRequestLimiter.Limit> second = captured(1);
        List<SharedRequestLimiter.Limit> firstAccount = accountLimits(first);
        assertThat(firstAccount)
                .extracting(SharedRequestLimiter.Limit::count)
                .containsExactlyInAnyOrder(10, 30);
        assertThat(firstAccount)
                .extracting(SharedRequestLimiter.Limit::duration)
                .containsExactlyInAnyOrder(Duration.ofMinutes(15), Duration.ofHours(1));
        assertThat(firstAccount).noneMatch(SharedRequestLimiter.Limit::sliding);
        assertThat(accountLimits(second)).isEqualTo(firstAccount);
    }

    @Test
    @DisplayName("결합 문자와 제어 문자를 섞은 표기도 원래 계정과 같은 계정 횟수를 쓴다")
    void accentVariantsShareAccountKey() {
        when(shared.acquire(any())).thenReturn(new SharedRequestLimiter.Decision(true, 0));

        limiter.acquire("member@example.com", "203.0.113.10");
        limiter.acquire("mémber@example.com", "203.0.113.11");
        limiter.acquire("m\u0001ember@example.com", "203.0.113.12");

        assertThat(accountLimits(captured(1))).isEqualTo(accountLimits(captured(0)));
        assertThat(accountLimits(captured(2))).isEqualTo(accountLimits(captured(0)));
    }

    @Test
    @DisplayName("계정 상한에 걸리면 틀린 비밀번호와 같은 429 응답으로 거부한다")
    void accountDenialUsesGenericLimitError() {
        when(shared.acquire(any()))
                .thenReturn(
                        new SharedRequestLimiter.Decision(true, 300, ACCOUNT_PREFIX + "short:abc"));

        assertThatThrownBy(() -> limiter.acquire("user@example.com", "203.0.113.10"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.LOGIN_ATTEMPT_LIMIT));
    }

    @Test
    @DisplayName("성공하면 조합과 계정 횟수를 지우고 IP 횟수는 하나만 돌려준다")
    void successResetsPairAndAccountCounts() {
        limiter.succeeded("user@example.com", "203.0.113.10");

        ArgumentCaptor<String> reset = ArgumentCaptor.forClass(String.class);
        verify(shared, times(3)).reset(reset.capture());
        assertThat(reset.getAllValues())
                .filteredOn(key -> key.startsWith(ACCOUNT_PREFIX))
                .hasSize(2);
        verify(shared).refund(anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 뒤에는 계정 횟수만 지운다")
    void clearAccountResetsOnlyAccountKeys() {
        limiter.clearAccount("user@example.com");

        ArgumentCaptor<String> reset = ArgumentCaptor.forClass(String.class);
        verify(shared, times(2)).reset(reset.capture());
        assertThat(reset.getAllValues()).allMatch(key -> key.startsWith(ACCOUNT_PREFIX));
    }

    @Test
    @DisplayName("성공 뒤 횟수 정리가 실패해도 로그인은 막지 않는다")
    void cleanupFailureDoesNotBreakLogin() {
        doThrow(new DataAccessResourceFailureException("down")).when(shared).reset(anyString());

        assertThatCode(() -> limiter.succeeded("user@example.com", "203.0.113.10"))
                .doesNotThrowAnyException();
        assertThatCode(() -> limiter.clearAccount("user@example.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("꺼져 있으면 집계 저장소를 쓰지 않는다")
    void disabledLimiterSkipsStore() {
        LoginAttemptLimiter disabled =
                new LoginAttemptLimiter(
                        shared,
                        new EmailNormalizer(),
                        false,
                        LoginAttemptLimiter.AccountLimits.DEFAULT);

        disabled.acquire("user@example.com", "203.0.113.10");
        disabled.succeeded("user@example.com", "203.0.113.10");
        disabled.clearAccount("user@example.com");

        verifyNoInteractions(shared);
    }

    @SuppressWarnings("unchecked")
    private List<SharedRequestLimiter.Limit> captured(int index) {
        return org.mockito.Mockito.mockingDetails(shared).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("acquire"))
                .map(invocation -> (List<SharedRequestLimiter.Limit>) invocation.getArgument(0))
                .toList()
                .get(index);
    }

    private static List<SharedRequestLimiter.Limit> accountLimits(
            List<SharedRequestLimiter.Limit> limits) {
        return limits.stream().filter(limit -> limit.key().startsWith(ACCOUNT_PREFIX)).toList();
    }
}
