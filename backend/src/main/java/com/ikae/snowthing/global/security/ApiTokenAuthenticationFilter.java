package com.ikae.snowthing.global.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.apitoken.service.ApiTokenService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.error.ErrorResponse;

import lombok.RequiredArgsConstructor;

/**
 * {@code Authorization: Bearer snt_...} 요청을 API 토큰 소유자로 인증한다. 권한은 토큰 범위만 준다.
 *
 * <p>허용 경로 밖이면 403으로 바로 끊는다. 범위 검사는 SecurityConfig가 다시 한다(두 겹). 토큰이 틀리면 세션 인증으로 넘기지 않고 401로
 * 끝낸다. 이 요청은 CSRF 검사를 건너뛰므로(브라우저가 Authorization 헤더를 자동으로 붙이지 않는다) 잘못된 토큰이 세션 쿠키로 처리되면 안 되기
 * 때문이다. 토큰 값은 로그에 남기지 않는다.
 */
@RequiredArgsConstructor
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_TOKEN_BEARER_PREFIX =
            BEARER_PREFIX + ApiTokenService.TOKEN_PREFIX;

    private static final List<AllowedRoute> ALLOWED_ROUTES =
            List.of(
                    new AllowedRoute(HttpMethod.GET, "^/api/v1/admin/search/evidence$"),
                    new AllowedRoute(HttpMethod.GET, "^/api/v1/admin/search/evidence/[^/]+$"),
                    new AllowedRoute(HttpMethod.POST, "^/api/v1/answer-drafts$"),
                    new AllowedRoute(HttpMethod.GET, "^/api/v1/answer-drafts/[0-9a-fA-F-]{36}$"));

    private final ApiTokenService apiTokenService;
    private final ObjectMapper objectMapper;

    public static boolean isApiTokenRequest(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.startsWith(API_TOKEN_BEARER_PREFIX);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isApiTokenRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            String rawToken =
                    request.getHeader(HttpHeaders.AUTHORIZATION).substring(BEARER_PREFIX.length());
            Optional<ApiTokenUserDetails> principal = apiTokenService.authenticate(rawToken);
            if (principal.isEmpty()) {
                writeError(response, ErrorCode.API_TOKEN_INVALID);
                return;
            }
            if (!isAllowed(request)) {
                writeError(response, ErrorCode.ACCESS_DENIED);
                return;
            }
            ApiTokenUserDetails userDetails = principal.get();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()));
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ALLOWED_ROUTES.stream()
                .anyMatch(
                        route ->
                                route.method().matches(request.getMethod())
                                        && route.pattern().matcher(path).matches());
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.from(errorCode));
    }

    private record AllowedRoute(HttpMethod method, Pattern pattern) {
        AllowedRoute(HttpMethod method, String regex) {
            this(method, Pattern.compile(regex));
        }
    }
}
