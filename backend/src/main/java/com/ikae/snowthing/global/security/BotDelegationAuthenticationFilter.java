package com.ikae.snowthing.global.security;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.error.ErrorResponse;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BotDelegationAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final List<Pattern> ALLOWED_READ_PATHS =
            List.of(
                    Pattern.compile("^/api/v1/master/(?:resorts|riding-styles)$"),
                    Pattern.compile("^/api/v1/resort-cams$"),
                    Pattern.compile("^/api/v1/resort-reports/today$"),
                    Pattern.compile("^/api/v1/posts(?:/[0-9a-fA-F-]{36})?$"),
                    Pattern.compile("^/api/v1/carpools(?:/[0-9a-fA-F-]{36})?$"),
                    Pattern.compile("^/api/v1/market-listings(?:/[0-9a-fA-F-]{36})?$"),
                    Pattern.compile("^/api/v1/market-listings/categories$"));

    private final BotDelegationTokenService tokenService;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (!tokenService.isEnabled()) {
                writeError(response, ErrorCode.INVALID_CREDENTIALS);
                return;
            }
            if (!HttpMethod.GET.matches(request.getMethod())
                    || !isAllowedPath(request.getRequestURI())) {
                writeError(response, ErrorCode.ACCESS_DENIED);
                return;
            }
            BotDelegationClaims claims =
                    tokenService.validate(authorization.substring(BEARER_PREFIX.length()));
            Member member =
                    memberRepository
                            .findByPublicId(claims.subject())
                            .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));
            BotDelegatedUserDetails userDetails = new BotDelegatedUserDetails(member);
            if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                writeError(response, ErrorCode.ACCESS_DENIED);
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, claims.tokenId(), userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (CustomException exception) {
            writeError(response, exception.getErrorCode());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isAllowedPath(String path) {
        return ALLOWED_READ_PATHS.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.from(errorCode));
    }
}
