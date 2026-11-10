package com.ikae.snowthing.domain.auth.controller;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.auth.service.AuthService;
import com.ikae.snowthing.domain.auth.service.LoginAttemptLimiter;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.MemberSessionRegistry;
import com.ikae.snowthing.global.security.RecentAuthentication;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int REMEMBER_ME_TIMEOUT_SECONDS = (int) Duration.ofDays(30).toSeconds();
    private static final int DEFAULT_SESSION_TIMEOUT_SECONDS =
            (int) Duration.ofHours(1).toSeconds();

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AuthService authService;
    private final MemberSessionRegistry memberSessionRegistry;
    private final LoginAttemptLimiter loginAttemptLimiter;
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;

    @PostMapping("/login")
    public ResponseEntity<MemberLoginResponse> login(
            @Valid @RequestBody MemberLoginRequest loginRequest,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        // 비밀번호를 검사하기 전에 시도 횟수를 먼저 쓴다. 차단 중이면 맞는 비밀번호도 여기서 429로 끝난다.
        String clientIp = clientIpResolver.resolve(httpRequest);
        loginAttemptLimiter.acquire(loginRequest.getEmail(), clientIp);

        // 1. Spring Security 표준 AuthenticationManager 위임 인증 처리
        Authentication authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(), loginRequest.getPassword());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        loginAttemptLimiter.succeeded(loginRequest.getEmail(), clientIp);

        // 2. SecurityContext 생성 및 ContextHolder 설정
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        // 3. 세션 고정 공격 방어(Session Fixation Protection) 및 시큐리티 표준 SecurityContextRepository 저장
        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);

        // 4. Remember-Me 세션 타임아웃 계산 및 적용 (별도 메서드 분리 및 상수 적용)
        int timeoutSeconds = calculateSessionTimeoutSeconds(loginRequest.isRememberMe());
        session.setMaxInactiveInterval(timeoutSeconds);
        RecentAuthentication.mark(session, Instant.now(clock));

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        memberSessionRegistry.register(userDetails.getPublicId(), session);
        // 인증과 등록 사이의 정지를 잡는다. 이유는 AuthService.ensureStillActive 참고.
        try {
            authService.ensureStillActive(userDetails.getPublicId());
        } catch (CustomException exception) {
            memberSessionRegistry.invalidateAll(userDetails.getPublicId());
            throw exception;
        }

        MemberLoginResponse response = authService.getMemberProfileByEmail(loginRequest.getEmail());
        return ResponseEntity.ok(response);
    }

    /** Remember-Me 체크 여부에 따른 세션 타임아웃 계산 (초 단위) */
    private int calculateSessionTimeoutSeconds(boolean rememberMe) {
        return rememberMe ? REMEMBER_ME_TIMEOUT_SECONDS : DEFAULT_SESSION_TIMEOUT_SECONDS;
    }
}
