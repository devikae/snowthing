package com.ikae.snowthing.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.SecurityContextRepository;

import com.ikae.snowthing.domain.auth.oauth2.OAuth2ExternalClient;
import com.ikae.snowthing.domain.auth.oauth2.OAuth2Properties;
import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.auth.service.OAuthAuthorizationIpRateLimiter;
import com.ikae.snowthing.domain.auth.service.SocialAuthService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.MemberSessionRegistry;
import com.ikae.snowthing.global.web.ClientIpResolver;

class OAuth2AuthControllerTest {

    private OAuth2ExternalClient oauth2ExternalClient;
    private SocialAuthService socialAuthService;
    private SecurityContextRepository securityContextRepository;
    private MemberSessionRegistry memberSessionRegistry;
    private OAuthAuthorizationIpRateLimiter authorizationIpRateLimiter;
    private ClientIpResolver clientIpResolver;
    private OAuth2AuthController controller;

    @BeforeEach
    void setUp() {
        oauth2ExternalClient = mock(OAuth2ExternalClient.class);
        socialAuthService = mock(SocialAuthService.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        memberSessionRegistry = mock(MemberSessionRegistry.class);
        authorizationIpRateLimiter = mock(OAuthAuthorizationIpRateLimiter.class);
        clientIpResolver = mock(ClientIpResolver.class);
        given(clientIpResolver.resolve(any())).willReturn("203.0.113.10");

        OAuth2Properties oauth2Properties =
                new OAuth2Properties("https://snowthing.org", 3000, 5000, null, null);
        Clock fixedClock =
                Clock.fixed(Instant.parse("2026-10-19T05:00:00Z"), ZoneId.of("Asia/Seoul"));

        controller =
                new OAuth2AuthController(
                        oauth2ExternalClient,
                        socialAuthService,
                        oauth2Properties,
                        securityContextRepository,
                        memberSessionRegistry,
                        authorizationIpRateLimiter,
                        clientIpResolver,
                        fixedClock);
    }

    @Test
    @DisplayName("소셜 인가 시작 후 콜백이 성공하면 세션이 발급되고 returnUrl로 302 리다이렉트된다")
    void authorizeAndCallback_Success_EstablishesSessionAndRedirects() {
        given(oauth2ExternalClient.buildAuthorizationUri(eq(SocialProvider.NAVER), anyString()))
                .willReturn(URI.create("https://nid.naver.com/oauth2.0/authorize?state=test"));

        MockHttpServletRequest authorizeRequest = new MockHttpServletRequest();
        ResponseEntity<Void> authorizeResponse =
                controller.authorize("naver", "/carpool", authorizeRequest);

        assertThat(authorizeResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(authorizeResponse.getHeaders().getLocation())
                .isEqualTo(URI.create("https://nid.naver.com/oauth2.0/authorize?state=test"));

        MockHttpSession session = (MockHttpSession) authorizeRequest.getSession(false);
        assertThat(session).isNotNull();
        OAuth2AuthController.OAuth2AuthorizationSession savedState =
                (OAuth2AuthController.OAuth2AuthorizationSession)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE);
        assertThat(savedState).isNotNull();
        assertThat(savedState.returnUrl()).isEqualTo("/carpool");

        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER, "naver-777", "socialuser@naver.com", true, "소셜보더");
        Member member =
                Member.builder()
                        .email("socialuser@naver.com")
                        .password(null)
                        .nickname("소셜보더")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();

        given(
                        oauth2ExternalClient.fetchUserProfile(
                                SocialProvider.NAVER, "auth-code-123", savedState.state()))
                .willReturn(profile);
        given(socialAuthService.authenticateOrRegister(profile)).willReturn(member);

        String beforeSessionId = session.getId();
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setSession(session);
        MockHttpServletResponse callbackHttpResponse = new MockHttpServletResponse();

        ResponseEntity<Void> callbackResponse =
                controller.callback(
                        "naver",
                        "auth-code-123",
                        savedState.state(),
                        null,
                        callbackRequest,
                        callbackHttpResponse);

        assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(callbackResponse.getHeaders().getLocation())
                .isEqualTo(URI.create("https://snowthing.org/carpool"));
        assertThat(session.getId()).isNotEqualTo(beforeSessionId);
        assertThat(session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE)).isNull();
        verify(securityContextRepository)
                .saveContext(
                        any(SecurityContext.class), eq(callbackRequest), eq(callbackHttpResponse));
        verify(memberSessionRegistry).register(eq(member.getPublicId()), eq(session));
    }

    @Test
    @DisplayName("콜백에서 state가 일치하지 않거나 재사용되면 /login?error=OAUTH_STATE_INVALID로 리다이렉트된다")
    void callback_InvalidState_RedirectsToLoginError() {
        given(oauth2ExternalClient.buildAuthorizationUri(eq(SocialProvider.GOOGLE), anyString()))
                .willReturn(URI.create("https://accounts.google.com/o/oauth2/v2/auth"));

        MockHttpServletRequest authorizeRequest = new MockHttpServletRequest();
        controller.authorize("google", null, authorizeRequest);
        MockHttpSession session = (MockHttpSession) authorizeRequest.getSession(false);

        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setSession(session);
        MockHttpServletResponse callbackHttpResponse = new MockHttpServletResponse();

        ResponseEntity<Void> response =
                controller.callback(
                        "google",
                        "code-1",
                        "tampered-state",
                        null,
                        callbackRequest,
                        callbackHttpResponse);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("https://snowthing.org/login?error=OAUTH_STATE_INVALID"));
    }

    @Test
    @DisplayName("지원 범위에서 제거된 카카오 로그인 요청은 OAUTH_PROVIDER_UNSUPPORTED로 거부된다")
    void authorize_Kakao_RedirectsToUnsupportedProviderError() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Void> response = controller.authorize("kakao", null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(
                        URI.create("https://snowthing.org/login?error=OAUTH_PROVIDER_UNSUPPORTED"));
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("인가 요청 제한을 초과하면 서버 세션을 만들기 전에 로그인 오류로 리다이렉트한다")
    void authorize_RateLimitExceeded_DoesNotCreateSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        willThrow(new CustomException(ErrorCode.OAUTH_RATE_LIMIT))
                .given(authorizationIpRateLimiter)
                .check("203.0.113.10");

        ResponseEntity<Void> response = controller.authorize("google", null, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("https://snowthing.org/login?error=OAUTH_RATE_LIMIT"));
        assertThat(request.getSession(false)).isNull();
        verify(oauth2ExternalClient, never())
                .buildAuthorizationUri(eq(SocialProvider.GOOGLE), anyString());
    }

    @Test
    @DisplayName("Open Redirect 공격용 returnUrl은 '/'로 안전하게 정제된다")
    void sanitizeReturnUrl_OpenRedirectAttempts_ReturnsRootPath() {
        assertThat(OAuth2AuthController.sanitizeReturnUrl("https://evil.com")).isEqualTo("/");
        assertThat(OAuth2AuthController.sanitizeReturnUrl("//evil.com")).isEqualTo("/");
        assertThat(OAuth2AuthController.sanitizeReturnUrl("/\\evil.com")).isEqualTo("/");
        assertThat(OAuth2AuthController.sanitizeReturnUrl("/search?q=a b")).isEqualTo("/");
        assertThat(OAuth2AuthController.sanitizeReturnUrl("/posts/123")).isEqualTo("/posts/123");
    }
}
