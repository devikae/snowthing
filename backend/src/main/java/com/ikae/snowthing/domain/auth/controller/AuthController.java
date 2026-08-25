package com.ikae.snowthing.domain.auth.controller;

import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int REMEMBER_ME_TIMEOUT_SECONDS = (int) Duration.ofDays(30).toSeconds();
    private static final int DEFAULT_SESSION_TIMEOUT_SECONDS = (int) Duration.ofHours(1).toSeconds();

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<MemberLoginResponse> login(
            @Valid @RequestBody MemberLoginRequest loginRequest,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        // 1. Spring Security 표준 AuthenticationManager 위임 인증 처리
        Authentication authenticationToken = new UsernamePasswordAuthenticationToken(
                loginRequest.getEmail(),
                loginRequest.getPassword()
        );
        Authentication authentication = authenticationManager.authenticate(authenticationToken);

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

        MemberLoginResponse response = authService.getMemberProfileByEmail(loginRequest.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * Remember-Me 체크 여부에 따른 세션 타임아웃 계산 (초 단위)
     */
    private int calculateSessionTimeoutSeconds(boolean rememberMe) {
        return rememberMe ? REMEMBER_ME_TIMEOUT_SECONDS : DEFAULT_SESSION_TIMEOUT_SECONDS;
    }
}
