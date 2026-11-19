package com.ikae.snowthing.global.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ikae.snowthing.domain.auth.service.AuthService;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 유지 쿠키로 새로 만든 세션을 회원별 세션 목록에 올린다. 목록에 없으면 관리자 정지, 비밀번호 변경, 탈퇴 때 이 세션을 끊지 못한다. 로그인 API와 소셜 로그인은
 * 컨트롤러에서 직접 올린다.
 *
 * <p>RememberMeAuthenticationFilter 바로 뒤에 둔다. 그 필터의 성공 처리기는 체인을 끝내 버려 쓰지 않는다.
 */
@RequiredArgsConstructor
public class RememberMeSessionRegistrationFilter extends OncePerRequestFilter {

    private final MemberSessionRegistry memberSessionRegistry;
    private final AuthService authService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getAttribute(SnowthingRememberMeServices.AUTO_LOGIN_ATTRIBUTE) != null) {
            register(request);
        }
        filterChain.doFilter(request, response);
    }

    private void register(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof RememberMeAuthenticationToken)
                || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            return;
        }
        HttpSession session = request.getSession(true);
        memberSessionRegistry.register(user.getPublicId(), session);
        // 회원 확인과 등록 사이에 정지되면 정지 처리가 이 세션을 못 보고 지나간다. 등록한 뒤 한 번 더 확인한다.
        try {
            authService.ensureStillActive(user.getPublicId());
        } catch (CustomException | CustomAuthException exception) {
            memberSessionRegistry.invalidateAll(user.getPublicId());
            SecurityContextHolder.clearContext();
        }
    }
}
