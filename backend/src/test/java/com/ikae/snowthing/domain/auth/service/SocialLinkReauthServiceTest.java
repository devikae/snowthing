package com.ikae.snowthing.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.RecentAuthentication;

class SocialLinkReauthServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final String IP = "203.0.113.10";
    private static final String EMAIL = "member@snowthing.org";

    private MemberRepository memberRepository;
    private PasswordEncoder passwordEncoder;
    private LoginAttemptLimiter limiter;
    private SocialLinkReauthService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        limiter = mock(LoginAttemptLimiter.class);
        service =
                new SocialLinkReauthService(
                        memberRepository,
                        passwordEncoder,
                        limiter,
                        Clock.fixed(NOW, ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("비밀번호 회원은 현재 비밀번호가 맞으면 통과하고 실패 횟수를 초기화한다")
    void passwordMemberWithCorrectPasswordPasses() {
        givenMember("encoded");
        given(passwordEncoder.matches("current", "encoded")).willReturn(true);

        assertThatCode(() -> service.confirm(1L, "current", null, IP)).doesNotThrowAnyException();

        InOrder order = inOrder(limiter, passwordEncoder);
        order.verify(limiter).acquire(EMAIL, IP);
        order.verify(passwordEncoder).matches("current", "encoded");
        order.verify(limiter).succeeded(EMAIL, IP);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 로그인 실패처럼 횟수를 쓰고 초기화하지 않는다")
    void wrongPasswordCountsTowardAccountLimit() {
        givenMember("encoded");
        given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

        assertThatThrownBy(() -> service.confirm(1L, "wrong", null, IP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        verify(limiter).acquire(EMAIL, IP);
        verify(limiter, never()).succeeded(any(), any());
    }

    @Test
    @DisplayName("한도에 걸렸으면 비밀번호를 검사하지 않는다")
    void limitedAccountDoesNotCheckPassword() {
        givenMember("encoded");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.LOGIN_ATTEMPT_LIMIT))
                .given(limiter)
                .acquire(EMAIL, IP);

        assertThatThrownBy(() -> service.confirm(1L, "current", null, IP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_ATTEMPT_LIMIT);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("비밀번호 회원이 비밀번호를 보내지 않으면 횟수를 쓰지 않고 거부한다")
    void missingPasswordIsRejected() {
        givenMember("encoded");

        assertThatThrownBy(() -> service.confirm(1L, null, null, IP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        verifyNoInteractions(limiter, passwordEncoder);
    }

    @Test
    @DisplayName("72바이트를 넘는 비밀번호로 예외가 나도 틀린 비밀번호와 같은 응답이다")
    void tooLongPasswordIsMismatch() {
        givenMember("encoded");
        given(passwordEncoder.matches(any(), any()))
                .willThrow(new IllegalArgumentException("too long"));

        assertThatThrownBy(() -> service.confirm(1L, "a".repeat(200), null, IP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
    }

    @Test
    @DisplayName("소셜 전용 회원은 10분 안에 로그인한 세션이면 통과한다")
    void socialOnlyMemberWithRecentLoginPasses() {
        givenMember(null);
        MockHttpSession session = new MockHttpSession();
        RecentAuthentication.mark(session, NOW.minusSeconds(9 * 60));

        assertThatCode(() -> service.confirm(1L, null, session, IP)).doesNotThrowAnyException();
        verifyNoInteractions(limiter, passwordEncoder);
    }

    @Test
    @DisplayName("소셜 전용 회원이 로그인한 지 10분이 넘었거나 기록이 없으면 다시 로그인하라고 거부한다")
    void socialOnlyMemberWithOldLoginIsRejected() {
        givenMember(null);
        MockHttpSession old = new MockHttpSession();
        RecentAuthentication.mark(old, NOW.minusSeconds(11 * 60));

        for (MockHttpSession session : new MockHttpSession[] {old, new MockHttpSession(), null}) {
            assertThatThrownBy(() -> service.confirm(1L, "ignored", session, IP))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SOCIAL_LINK_REAUTH_REQUIRED);
        }
    }

    private void givenMember(String encodedPassword) {
        Member member =
                Member.builder()
                        .publicId("member-1")
                        .email(EMAIL)
                        .password(encodedPassword)
                        .nickname("라이더")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
    }
}
