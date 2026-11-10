package com.ikae.snowthing.domain.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.RecentAuthentication;

import lombok.RequiredArgsConstructor;

/**
 * 소셜 계정 연결은 이 계정에 새 로그인 방법을 더하는 일이라, 잠깐 자리를 비운 사이 세션만 가진 사람이 자기 소셜 계정을 붙이지 못하게 다시 확인한다.
 *
 * <p>비밀번호가 있으면 현재 비밀번호를 받고, 틀린 횟수는 로그인 실패와 같은 계정 한도로 센다. 비밀번호가 없는 소셜 전용 회원은 확인할 값이 없으므로 최근에 로그인한
 * 세션에서만 허용한다.
 */
@Service
@RequiredArgsConstructor
public class SocialLinkReauthService {

    static final Duration RECENT_LOGIN = Duration.ofMinutes(10);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final Clock clock;

    @Transactional(readOnly = true)
    public void confirm(
            Long memberId, String currentPassword, HttpSession session, String clientIp) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.LOGIN_REQUIRED));
        if (member.getPassword() == null) {
            if (!RecentAuthentication.isWithin(session, Instant.now(clock), RECENT_LOGIN)) {
                throw new CustomException(ErrorCode.SOCIAL_LINK_REAUTH_REQUIRED);
            }
            return;
        }
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw new CustomException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        loginAttemptLimiter.acquire(member.getEmail(), clientIp);
        if (!matches(currentPassword, member.getPassword())) {
            throw new CustomException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        loginAttemptLimiter.succeeded(member.getEmail(), clientIp);
    }

    // BCrypt는 72바이트를 넘는 입력에 예외를 던질 수 있다. 틀린 비밀번호와 같은 응답으로 돌린다.
    private boolean matches(String raw, String encoded) {
        try {
            return passwordEncoder.matches(raw, encoded);
        } catch (IllegalArgumentException tooLong) {
            return false;
        }
    }
}
