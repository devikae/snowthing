package com.ikae.snowthing.domain.auth.controller;

import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.ikae.snowthing.domain.auth.oauth2.OAuth2ExternalClient;
import com.ikae.snowthing.domain.auth.oauth2.OAuth2Properties;
import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.auth.service.OAuthAuthorizationIpRateLimiter;
import com.ikae.snowthing.domain.auth.service.SocialAuthService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.MemberSessionRegistry;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
@RequiredArgsConstructor
public class OAuth2AuthController {

    static final String SESSION_STATE_ATTRIBUTE = "SNOWTHING_OAUTH2_AUTH_STATE";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_SESSION_TIMEOUT_SECONDS =
            (int) Duration.ofHours(1).toSeconds();
    private static final int STATE_BYTES_LENGTH = 32;

    private final OAuth2ExternalClient oauth2ExternalClient;
    private final SocialAuthService socialAuthService;
    private final OAuth2Properties oauth2Properties;
    private final SecurityContextRepository securityContextRepository;
    private final MemberSessionRegistry memberSessionRegistry;
    private final OAuthAuthorizationIpRateLimiter authorizationIpRateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @GetMapping("/authorize/{provider}")
    public ResponseEntity<Void> authorize(
            @PathVariable String provider,
            @RequestParam(required = false) String returnUrl,
            HttpServletRequest httpRequest) {
        try {
            SocialProvider socialProvider = SocialProvider.from(provider);
            authorizationIpRateLimiter.check(clientIpResolver.resolve(httpRequest));
            String state = generateStateToken();
            String safeReturnUrl = sanitizeReturnUrl(returnUrl);
            URI authorizationUri =
                    oauth2ExternalClient.buildAuthorizationUri(socialProvider, state);

            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(
                    SESSION_STATE_ATTRIBUTE,
                    new OAuth2AuthorizationSession(
                            socialProvider,
                            state,
                            safeReturnUrl,
                            Instant.now(clock).plus(STATE_TTL)));

            return ResponseEntity.status(HttpStatus.FOUND).location(authorizationUri).build();
        } catch (CustomException exception) {
            return redirectToLoginError(exception.getErrorCode());
        }
    }

    @GetMapping("/callback/{provider}")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            SocialProvider socialProvider = SocialProvider.from(provider);
            OAuth2AuthorizationSession savedState =
                    consumeAndValidateState(httpRequest, socialProvider, state);

            if (error != null && !error.isBlank()) {
                return redirectToLoginError(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
            }

            OAuthUserProfile profile =
                    oauth2ExternalClient.fetchUserProfile(socialProvider, code, state);
            Member member = socialAuthService.authenticateOrRegister(profile);

            establishAuthenticatedSession(member, httpRequest, httpResponse);

            URI redirectUri =
                    URI.create(
                            oauth2Properties.effectiveFrontendBaseUrl() + savedState.returnUrl());
            return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
        } catch (CustomException exception) {
            return redirectToLoginError(exception.getErrorCode());
        }
    }

    private OAuth2AuthorizationSession consumeAndValidateState(
            HttpServletRequest httpRequest, SocialProvider provider, String requestState) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            throw new CustomException(ErrorCode.OAUTH_STATE_INVALID);
        }
        Object attribute = session.getAttribute(SESSION_STATE_ATTRIBUTE);
        session.removeAttribute(SESSION_STATE_ATTRIBUTE);
        if (!(attribute instanceof OAuth2AuthorizationSession savedState)) {
            throw new CustomException(ErrorCode.OAUTH_STATE_INVALID);
        }
        if (requestState == null
                || requestState.isBlank()
                || savedState.provider() != provider
                || !Instant.now(clock).isBefore(savedState.expiresAt())
                || !MessageDigest.isEqual(
                        savedState.state().getBytes(StandardCharsets.UTF_8),
                        requestState.getBytes(StandardCharsets.UTF_8))) {
            throw new CustomException(ErrorCode.OAUTH_STATE_INVALID);
        }
        return savedState;
    }

    private void establishAuthenticatedSession(
            Member member, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        CustomUserDetails userDetails = new CustomUserDetails(member);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();
        securityContextRepository.saveContext(securityContext, httpRequest, httpResponse);
        session.setMaxInactiveInterval(DEFAULT_SESSION_TIMEOUT_SECONDS);
        memberSessionRegistry.register(userDetails.getPublicId(), session);
    }

    private ResponseEntity<Void> redirectToLoginError(ErrorCode errorCode) {
        URI loginErrorUri =
                UriComponentsBuilder.fromUriString(
                                oauth2Properties.effectiveFrontendBaseUrl() + "/login")
                        .queryParam("error", errorCode.name())
                        .encode()
                        .build()
                        .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(loginErrorUri).build();
    }

    private String generateStateToken() {
        byte[] bytes = new byte[STATE_BYTES_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sanitizeReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return "/";
        }
        String trimmed = returnUrl.trim();
        if (!trimmed.startsWith("/")
                || trimmed.startsWith("//")
                || trimmed.contains("\\")
                || trimmed.contains("\r")
                || trimmed.contains("\n")) {
            return "/";
        }
        try {
            new URI(trimmed);
        } catch (URISyntaxException exception) {
            return "/";
        }
        return trimmed;
    }

    record OAuth2AuthorizationSession(
            SocialProvider provider, String state, String returnUrl, Instant expiresAt)
            implements Serializable {}
}
