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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.ikae.snowthing.domain.auth.dto.SocialSignUpRequest;
import com.ikae.snowthing.domain.auth.oauth2.OAuth2ExternalClient;
import com.ikae.snowthing.domain.auth.oauth2.OAuth2Properties;
import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.auth.service.AuthService;
import com.ikae.snowthing.domain.auth.service.OAuthAuthorizationIpRateLimiter;
import com.ikae.snowthing.domain.auth.service.SocialAuthService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.MemberSessionRegistry;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth/oauth2")
@RequiredArgsConstructor
public class OAuth2AuthController {

    static final String SESSION_STATE_ATTRIBUTE = "SNOWTHING_OAUTH2_AUTH_STATE";
    static final String SESSION_PENDING_SIGNUP_ATTRIBUTE = "SNOWTHING_OAUTH2_PENDING_SIGNUP";
    static final String PROFILE_PATH = "/profile";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int DEFAULT_SESSION_TIMEOUT_SECONDS =
            (int) Duration.ofHours(1).toSeconds();
    private static final int STATE_BYTES_LENGTH = 32;
    private static final int MAX_PENDING_AUTHORIZATIONS = 5;

    private final OAuth2ExternalClient oauth2ExternalClient;
    private final SocialAuthService socialAuthService;
    private final AuthService authService;
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
            addPendingAuthorization(
                    session,
                    new OAuth2AuthorizationSession(
                            socialProvider,
                            state,
                            safeReturnUrl,
                            Instant.now(clock).plus(STATE_TTL),
                            null));

            return ResponseEntity.status(HttpStatus.FOUND).location(authorizationUri).build();
        } catch (CustomException exception) {
            return redirectToLoginError(exception.getErrorCode());
        }
    }

    /**
     * 로그인한 회원이 다른 소셜 로그인을 연결할 때 쓴다. CSRF 토큰을 받는 POST로 시작하고, 이동할 주소만 돌려준다.
     *
     * <p>연결할 회원 id는 클라이언트 값이 아니라 지금 세션의 인증 정보에서 꺼내 state와 함께 서버 세션에 둔다.
     */
    @PostMapping("/link/{provider}")
    public ResponseEntity<SocialLinkStartResponse> startLink(
            @PathVariable String provider,
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletRequest httpRequest) {
        Long memberId = requireSessionMemberId(user);
        SocialProvider socialProvider = SocialProvider.from(provider);
        authorizationIpRateLimiter.check(clientIpResolver.resolve(httpRequest));
        socialAuthService.requireProviderNotLinked(memberId, socialProvider);
        String state = generateStateToken();
        URI authorizationUri = oauth2ExternalClient.buildAuthorizationUri(socialProvider, state);

        addPendingAuthorization(
                httpRequest.getSession(true),
                new OAuth2AuthorizationSession(
                        socialProvider,
                        state,
                        PROFILE_PATH,
                        Instant.now(clock).plus(STATE_TTL),
                        memberId));
        return ResponseEntity.ok(new SocialLinkStartResponse(authorizationUri.toString()));
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
            if (savedState.linkMemberId() != null) {
                return completeLink(socialProvider, savedState, code, state, error);
            }

            if (error != null && !error.isBlank()) {
                return redirectToLoginError(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
            }

            OAuthUserProfile profile =
                    oauth2ExternalClient.fetchUserProfile(socialProvider, code, state);
            Optional<Member> member = socialAuthService.findMemberForLogin(profile);
            if (member.isEmpty()) {
                httpRequest
                        .getSession(true)
                        .setAttribute(
                                SESSION_PENDING_SIGNUP_ATTRIBUTE,
                                PendingSocialSignUp.of(
                                        profile,
                                        savedState.returnUrl(),
                                        Instant.now(clock).plus(STATE_TTL)));
                return redirectToFrontend("/signup/social");
            }

            establishAuthenticatedSession(member.get(), httpRequest, httpResponse);
            return redirectToFrontend(savedState.returnUrl());
        } catch (CustomException exception) {
            return redirectToLoginError(exception.getErrorCode());
        }
    }

    private ResponseEntity<Void> completeLink(
            SocialProvider provider,
            OAuth2AuthorizationSession savedState,
            String code,
            String state,
            String error) {
        try {
            if (error != null && !error.isBlank()) {
                throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
            }
            // 시작한 뒤 로그아웃하고 다른 계정으로 로그인했다면 세션 속성은 남아 있어도 회원이 바뀐다.
            Long sessionMemberId = currentSessionMemberId();
            if (!savedState.linkMemberId().equals(sessionMemberId)) {
                throw new CustomException(ErrorCode.SOCIAL_LINK_SESSION_MISMATCH);
            }
            OAuthUserProfile profile = oauth2ExternalClient.fetchUserProfile(provider, code, state);
            socialAuthService.linkSocialAccount(sessionMemberId, profile);
            return redirectToProfile("socialLinked", provider.name());
        } catch (CustomException exception) {
            return redirectToProfile("socialLinkError", exception.getErrorCode().name());
        }
    }

    private Long requireSessionMemberId(CustomUserDetails user) {
        if (user == null || user instanceof BotDelegatedUserDetails || user.getMemberId() == null) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        return user.getMemberId();
    }

    private Long currentSessionMemberId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof CustomUserDetails user
                && !(user instanceof BotDelegatedUserDetails)) {
            return user.getMemberId();
        }
        return null;
    }

    private ResponseEntity<Void> redirectToProfile(String parameter, String value) {
        URI uri =
                UriComponentsBuilder.fromUriString(
                                oauth2Properties.effectiveFrontendBaseUrl() + PROFILE_PATH)
                        .queryParam(parameter, value)
                        .encode()
                        .build()
                        .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
    }

    @GetMapping("/signup/pending")
    public ResponseEntity<PendingSocialSignUpResponse> pendingSignUp(
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                new PendingSocialSignUpResponse(requirePendingSignUp(httpRequest).provider()));
    }

    @PostMapping("/signup")
    public ResponseEntity<SocialSignUpResponse> completeSignUp(
            @Valid @RequestBody SocialSignUpRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        PendingSocialSignUp pending = requirePendingSignUp(httpRequest);
        Member member = socialAuthService.completeSignUp(pending.toProfile(), request);
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.removeAttribute(SESSION_PENDING_SIGNUP_ATTRIBUTE);
        }
        establishAuthenticatedSession(member, httpRequest, httpResponse);
        return ResponseEntity.ok(new SocialSignUpResponse(pending.returnUrl()));
    }

    private PendingSocialSignUp requirePendingSignUp(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null
                && session.getAttribute(SESSION_PENDING_SIGNUP_ATTRIBUTE)
                        instanceof PendingSocialSignUp pending
                && Instant.now(clock).isBefore(pending.expiresAt())) {
            return pending;
        }
        throw new CustomException(ErrorCode.OAUTH_SIGNUP_EXPIRED);
    }

    private ResponseEntity<Void> redirectToFrontend(String path) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(oauth2Properties.effectiveFrontendBaseUrl() + path))
                .build();
    }

    private OAuth2AuthorizationSession consumeAndValidateState(
            HttpServletRequest httpRequest, SocialProvider provider, String requestState) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            throw new CustomException(ErrorCode.OAUTH_STATE_INVALID);
        }
        Object attribute = session.getAttribute(SESSION_STATE_ATTRIBUTE);
        if (!(attribute instanceof OAuth2AuthorizationSessions savedStates)) {
            throw new CustomException(ErrorCode.OAUTH_STATE_INVALID);
        }

        Instant now = Instant.now(clock);
        OAuth2AuthorizationSession matchedState = null;
        List<OAuth2AuthorizationSession> remainingStates = new ArrayList<>();
        for (OAuth2AuthorizationSession candidate : savedStates.pending()) {
            boolean matches = constantTimeEquals(candidate.state(), requestState);
            if (matches && candidate.provider() == provider) {
                if (matchedState == null) {
                    matchedState = candidate;
                }
            } else if (now.isBefore(candidate.expiresAt())) {
                remainingStates.add(candidate);
            }
        }
        storePendingAuthorizations(session, remainingStates);

        if (matchedState == null
                || matchedState.provider() != provider
                || !now.isBefore(matchedState.expiresAt())) {
            throw new CustomException(ErrorCode.OAUTH_STATE_INVALID);
        }
        return matchedState;
    }

    private void addPendingAuthorization(
            HttpSession session, OAuth2AuthorizationSession authorization) {
        Instant now = Instant.now(clock);
        List<OAuth2AuthorizationSession> pending = new ArrayList<>();
        Object attribute = session.getAttribute(SESSION_STATE_ATTRIBUTE);
        if (attribute instanceof OAuth2AuthorizationSessions savedStates) {
            savedStates.pending().stream()
                    .filter(saved -> now.isBefore(saved.expiresAt()))
                    .forEach(pending::add);
        }
        int overflow = pending.size() - MAX_PENDING_AUTHORIZATIONS + 1;
        if (overflow > 0) {
            pending.subList(0, overflow).clear();
        }
        pending.add(authorization);
        storePendingAuthorizations(session, pending);
    }

    private void storePendingAuthorizations(
            HttpSession session, List<OAuth2AuthorizationSession> pending) {
        if (pending.isEmpty()) {
            session.removeAttribute(SESSION_STATE_ATTRIBUTE);
            return;
        }
        session.setAttribute(SESSION_STATE_ATTRIBUTE, new OAuth2AuthorizationSessions(pending));
    }

    private boolean constantTimeEquals(String savedState, String requestState) {
        if (requestState == null || requestState.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                savedState.getBytes(StandardCharsets.UTF_8),
                requestState.getBytes(StandardCharsets.UTF_8));
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
        // 인증과 등록 사이의 정지를 잡는다. 이유는 AuthService.ensureStillActive 참고.
        try {
            authService.ensureStillActive(userDetails.getPublicId());
        } catch (CustomException exception) {
            memberSessionRegistry.invalidateAll(userDetails.getPublicId());
            throw exception;
        }
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

    public record PendingSocialSignUpResponse(SocialProvider provider) {}

    public record SocialSignUpResponse(String returnUrl) {}

    public record SocialLinkStartResponse(String authorizationUrl) {}

    /** 동의를 마칠 때까지 소셜 프로필을 서버 세션에만 둔다. 클라이언트가 값을 고칠 수 없다. */
    record PendingSocialSignUp(
            SocialProvider provider,
            String providerUserId,
            String email,
            boolean emailAuthoritative,
            String nickname,
            String returnUrl,
            Instant expiresAt)
            implements Serializable {

        static PendingSocialSignUp of(
                OAuthUserProfile profile, String returnUrl, Instant expiresAt) {
            return new PendingSocialSignUp(
                    profile.provider(),
                    profile.providerUserId(),
                    profile.email(),
                    profile.emailAuthoritative(),
                    profile.nickname(),
                    returnUrl,
                    expiresAt);
        }

        OAuthUserProfile toProfile() {
            return new OAuthUserProfile(
                    provider, providerUserId, email, emailAuthoritative, nickname);
        }
    }

    /** linkMemberId가 있으면 로그인이 아니라 그 회원에게 소셜 계정을 연결하는 요청이다. */
    record OAuth2AuthorizationSession(
            SocialProvider provider,
            String state,
            String returnUrl,
            Instant expiresAt,
            Long linkMemberId)
            implements Serializable {}

    record OAuth2AuthorizationSessions(List<OAuth2AuthorizationSession> pending)
            implements Serializable {

        OAuth2AuthorizationSessions {
            pending = List.copyOf(pending);
        }
    }
}
