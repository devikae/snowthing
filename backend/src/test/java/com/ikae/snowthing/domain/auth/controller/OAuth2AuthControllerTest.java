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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.auth.dto.SocialSignUpRequest;
import com.ikae.snowthing.domain.auth.oauth2.OAuth2ExternalClient;
import com.ikae.snowthing.domain.auth.oauth2.OAuth2Properties;
import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.auth.service.AuthService;
import com.ikae.snowthing.domain.auth.service.OAuthAuthorizationIpRateLimiter;
import com.ikae.snowthing.domain.auth.service.SocialAuthService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.MemberSessionRegistry;
import com.ikae.snowthing.global.web.ClientIpResolver;

class OAuth2AuthControllerTest {

    private OAuth2ExternalClient oauth2ExternalClient;
    private SocialAuthService socialAuthService;
    private AuthService authService;
    private SecurityContextRepository securityContextRepository;
    private MemberSessionRegistry memberSessionRegistry;
    private OAuthAuthorizationIpRateLimiter authorizationIpRateLimiter;
    private ClientIpResolver clientIpResolver;
    private OAuth2AuthController controller;

    @BeforeEach
    void setUp() {
        oauth2ExternalClient = mock(OAuth2ExternalClient.class);
        socialAuthService = mock(SocialAuthService.class);
        authService = mock(AuthService.class);
        securityContextRepository = mock(SecurityContextRepository.class);
        memberSessionRegistry = mock(MemberSessionRegistry.class);
        authorizationIpRateLimiter = mock(OAuthAuthorizationIpRateLimiter.class);
        clientIpResolver = mock(ClientIpResolver.class);
        given(clientIpResolver.resolve(any())).willReturn("203.0.113.10");

        OAuth2Properties oauth2Properties =
                new OAuth2Properties("https://snowthing.org", 3000, 5000, null, null, null);
        Clock fixedClock =
                Clock.fixed(Instant.parse("2026-10-19T05:00:00Z"), ZoneId.of("Asia/Seoul"));

        controller =
                new OAuth2AuthController(
                        oauth2ExternalClient,
                        socialAuthService,
                        authService,
                        oauth2Properties,
                        securityContextRepository,
                        memberSessionRegistry,
                        authorizationIpRateLimiter,
                        clientIpResolver,
                        fixedClock);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static CustomUserDetails member(long id, String publicId) {
        Member member =
                Member.builder()
                        .publicId(publicId)
                        .email(publicId + "@snowthing.org")
                        .password("encoded")
                        .nickname(publicId)
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        ReflectionTestUtils.setField(member, "id", id);
        return new CustomUserDetails(member);
    }

    private static void loginAs(CustomUserDetails user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    private static OAuth2AuthController.OAuth2AuthorizationSession pendingAuthorization(
            MockHttpSession session) {
        return ((OAuth2AuthController.OAuth2AuthorizationSessions)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE))
                .pending()
                .get(0);
    }

    private MockHttpSession startLink(CustomUserDetails user) {
        given(oauth2ExternalClient.buildAuthorizationUri(eq(SocialProvider.KAKAO), anyString()))
                .willReturn(URI.create("https://kauth.kakao.com/oauth/authorize"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        var response = controller.startLink("kakao", user, request);
        assertThat(response.getBody().authorizationUrl())
                .isEqualTo("https://kauth.kakao.com/oauth/authorize");
        return (MockHttpSession) request.getSession(false);
    }

    private ResponseEntity<Void> linkCallback(MockHttpSession session, String state) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return controller.callback(
                "kakao", "code", state, null, request, new MockHttpServletResponse());
    }

    @Test
    @DisplayName("로그인한 회원이 연결을 마치면 지금 회원에게 붙이고 세션은 새로 만들지 않는다")
    void link_Success_AttachesToSessionMemberAndRedirectsToProfile() {
        CustomUserDetails user = member(7L, "member-7");
        MockHttpSession session = startLink(user);
        OAuth2AuthController.OAuth2AuthorizationSession saved = pendingAuthorization(session);
        assertThat(saved.linkMemberId()).isEqualTo(7L);
        String state = saved.state();
        OAuthUserProfile profile =
                new OAuthUserProfile(SocialProvider.KAKAO, "kakao-1", "k@kakao.com", true, "보더");
        given(oauth2ExternalClient.fetchUserProfile(SocialProvider.KAKAO, "code", state))
                .willReturn(profile);
        loginAs(user);

        ResponseEntity<Void> response = linkCallback(session, state);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("https://snowthing.org/profile?socialLinked=KAKAO"));
        verify(socialAuthService).linkSocialAccount(7L, profile);
        verify(socialAuthService, never()).findMemberForLogin(any());
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    @Test
    @DisplayName("로그인하지 않은 사용자는 연결을 시작할 수 없다")
    void startLink_Unauthenticated_Rejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> controller.startLink("kakao", null, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOGIN_REQUIRED);
        assertThat(request.getSession(false)).isNull();
        verify(oauth2ExternalClient, never()).buildAuthorizationUri(any(), anyString());
    }

    @Test
    @DisplayName("이미 같은 제공자가 연결돼 있으면 소셜 로그인 화면으로 보내지 않는다")
    void startLink_ProviderAlreadyLinked_Rejected() {
        CustomUserDetails user = member(7L, "member-7");
        willThrow(new CustomException(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED))
                .given(socialAuthService)
                .requireProviderNotLinked(7L, SocialProvider.KAKAO);
        MockHttpServletRequest request = new MockHttpServletRequest();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> controller.startLink("kakao", user, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    @DisplayName("연결을 시작한 뒤 세션의 회원이 바뀌면 토큰을 교환하지 않고 거부한다")
    void linkCallback_SessionMemberChanged_Rejected() {
        MockHttpSession session = startLink(member(7L, "member-7"));
        String state = pendingAuthorization(session).state();
        loginAs(member(8L, "member-8"));

        ResponseEntity<Void> response = linkCallback(session, state);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(
                        URI.create(
                                "https://snowthing.org/profile?socialLinkError=SOCIAL_LINK_SESSION_MISMATCH"));
        verify(oauth2ExternalClient, never()).fetchUserProfile(any(), any(), any());
        verify(socialAuthService, never()).linkSocialAccount(any(), any());
    }

    @Test
    @DisplayName("연결 도중 로그아웃해 인증 정보가 없으면 거부한다")
    void linkCallback_LoggedOut_Rejected() {
        MockHttpSession session = startLink(member(7L, "member-7"));
        String state = pendingAuthorization(session).state();

        ResponseEntity<Void> response = linkCallback(session, state);

        assertThat(response.getHeaders().getLocation().toString())
                .endsWith("socialLinkError=SOCIAL_LINK_SESSION_MISMATCH");
        verify(socialAuthService, never()).linkSocialAccount(any(), any());
    }

    @Test
    @DisplayName("다른 눈팅 계정이 쓰는 소셜 계정이면 내 정보 화면에 이유를 전한다")
    void linkCallback_AccountInUse_RedirectsWithError() {
        CustomUserDetails user = member(7L, "member-7");
        MockHttpSession session = startLink(user);
        String state = pendingAuthorization(session).state();
        OAuthUserProfile profile =
                new OAuthUserProfile(SocialProvider.KAKAO, "kakao-1", "k@kakao.com", true, "보더");
        given(oauth2ExternalClient.fetchUserProfile(SocialProvider.KAKAO, "code", state))
                .willReturn(profile);
        willThrow(new CustomException(ErrorCode.SOCIAL_ACCOUNT_IN_USE))
                .given(socialAuthService)
                .linkSocialAccount(7L, profile);
        loginAs(user);

        ResponseEntity<Void> response = linkCallback(session, state);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(
                        URI.create(
                                "https://snowthing.org/profile?socialLinkError=SOCIAL_ACCOUNT_IN_USE"));
    }

    private MockHttpSession startPendingSignUp() {
        given(oauth2ExternalClient.buildAuthorizationUri(eq(SocialProvider.NAVER), anyString()))
                .willReturn(URI.create("https://nid.naver.com/oauth2.0/authorize"));
        MockHttpServletRequest authorizeRequest = new MockHttpServletRequest();
        controller.authorize("naver", "/carpool", authorizeRequest);
        MockHttpSession session = (MockHttpSession) authorizeRequest.getSession(false);
        String state =
                ((OAuth2AuthController.OAuth2AuthorizationSessions)
                                session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE))
                        .pending()
                        .get(0)
                        .state();
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER, "naver-new", "new@naver.com", false, "새보더");
        given(oauth2ExternalClient.fetchUserProfile(SocialProvider.NAVER, "code", state))
                .willReturn(profile);
        given(socialAuthService.findMemberForLogin(profile)).willReturn(java.util.Optional.empty());
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setSession(session);
        ResponseEntity<Void> response =
                controller.callback(
                        "naver",
                        "code",
                        state,
                        null,
                        callbackRequest,
                        new MockHttpServletResponse());
        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("https://snowthing.org/signup/social"));
        return session;
    }

    @Test
    @DisplayName("처음 온 소셜 사용자는 로그인되지 않고 동의 화면으로 이동한다")
    void callback_NewUser_RedirectsToConsentWithoutLogin() {
        MockHttpSession session = startPendingSignUp();

        verify(socialAuthService, never()).completeSignUp(any(), any());
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
        assertThat(session.getAttribute(OAuth2AuthController.SESSION_PENDING_SIGNUP_ATTRIBUTE))
                .isNotNull();
    }

    @Test
    @DisplayName("동의를 마치면 회원을 만들고 세션을 발급하며 대기 정보를 지운다")
    void completeSignUp_PendingSession_LogsInAndClearsPending() {
        MockHttpSession session = startPendingSignUp();
        Member member =
                Member.builder()
                        .email("oauth@users.invalid")
                        .nickname("새보더")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        SocialSignUpRequest request = SocialSignUpRequest.builder().build();
        given(socialAuthService.completeSignUp(any(), eq(request))).willReturn(member);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);

        var response =
                controller.completeSignUp(request, httpRequest, new MockHttpServletResponse());

        assertThat(response.getBody().returnUrl()).isEqualTo("/carpool");
        assertThat(session.getAttribute(OAuth2AuthController.SESSION_PENDING_SIGNUP_ATTRIBUTE))
                .isNull();
        verify(securityContextRepository).saveContext(any(SecurityContext.class), any(), any());
    }

    @Test
    @DisplayName("콜백을 거치지 않은 가입 요청은 거부한다")
    void completeSignUp_NoPendingSession_Rejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                controller.completeSignUp(
                                        SocialSignUpRequest.builder().build(),
                                        new MockHttpServletRequest(),
                                        new MockHttpServletResponse()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.OAUTH_SIGNUP_EXPIRED);
        verify(socialAuthService, never()).completeSignUp(any(), any());
    }

    @Test
    @DisplayName("인증 뒤 세션 등록 전에 정지되면 세션을 끊고 로그인 오류로 보낸다")
    void callback_SuspendedDuringLogin_InvalidatesAndRedirectsToLoginError() {
        given(oauth2ExternalClient.buildAuthorizationUri(eq(SocialProvider.NAVER), anyString()))
                .willReturn(URI.create("https://nid.naver.com/oauth2.0/authorize?state=test"));
        MockHttpServletRequest authorizeRequest = new MockHttpServletRequest();
        controller.authorize("naver", "/carpool", authorizeRequest);
        MockHttpSession session = (MockHttpSession) authorizeRequest.getSession(false);
        OAuth2AuthController.OAuth2AuthorizationSessions savedStates =
                (OAuth2AuthController.OAuth2AuthorizationSessions)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE);
        String state = savedStates.pending().get(0).state();
        OAuthUserProfile profile =
                new OAuthUserProfile(SocialProvider.NAVER, "naver-1", "s@naver.com", true, "보더");
        Member member =
                Member.builder()
                        .publicId("member-1")
                        .email("s@naver.com")
                        .nickname("보더")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        given(oauth2ExternalClient.fetchUserProfile(SocialProvider.NAVER, "code", state))
                .willReturn(profile);
        given(socialAuthService.findMemberForLogin(profile))
                .willReturn(java.util.Optional.of(member));
        willThrow(new CustomException(ErrorCode.ACCOUNT_SUSPENDED))
                .given(authService)
                .ensureStillActive("member-1");
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setSession(session);

        ResponseEntity<Void> response =
                controller.callback(
                        "naver",
                        "code",
                        state,
                        null,
                        callbackRequest,
                        new MockHttpServletResponse());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/login");
        assertThat(response.getHeaders().getLocation().getQuery())
                .isEqualTo("error=ACCOUNT_SUSPENDED");
        verify(memberSessionRegistry).invalidateAll("member-1");
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
        OAuth2AuthController.OAuth2AuthorizationSessions savedStates =
                (OAuth2AuthController.OAuth2AuthorizationSessions)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE);
        OAuth2AuthController.OAuth2AuthorizationSession savedState = savedStates.pending().get(0);
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
        given(socialAuthService.findMemberForLogin(profile))
                .willReturn(java.util.Optional.of(member));

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
        OAuth2AuthController.OAuth2AuthorizationSessions remainingStates =
                (OAuth2AuthController.OAuth2AuthorizationSessions)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE);
        assertThat(remainingStates.pending()).hasSize(1);
    }

    @Test
    @DisplayName("다른 제공자 콜백에 전달된 state는 소비하지 않아 원래 제공자 콜백에서 사용할 수 있다")
    void callback_WrongProvider_DoesNotConsumePendingState() {
        given(oauth2ExternalClient.buildAuthorizationUri(eq(SocialProvider.GOOGLE), anyString()))
                .willReturn(URI.create("https://accounts.google.com/o/oauth2/v2/auth"));
        MockHttpServletRequest authorizeRequest = new MockHttpServletRequest();
        controller.authorize("google", "/posts", authorizeRequest);
        MockHttpSession session = (MockHttpSession) authorizeRequest.getSession(false);
        OAuth2AuthController.OAuth2AuthorizationSessions savedStates =
                (OAuth2AuthController.OAuth2AuthorizationSessions)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE);
        String googleState = savedStates.pending().get(0).state();

        MockHttpServletRequest wrongProviderRequest = new MockHttpServletRequest();
        wrongProviderRequest.setSession(session);
        ResponseEntity<Void> wrongProviderResponse =
                controller.callback(
                        "naver",
                        "wrong-provider-code",
                        googleState,
                        null,
                        wrongProviderRequest,
                        new MockHttpServletResponse());
        assertThat(wrongProviderResponse.getHeaders().getLocation())
                .isEqualTo(URI.create("https://snowthing.org/login?error=OAUTH_STATE_INVALID"));

        OAuth2AuthController.OAuth2AuthorizationSessions remainingStates =
                (OAuth2AuthController.OAuth2AuthorizationSessions)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE);
        assertThat(remainingStates.pending())
                .extracting(OAuth2AuthController.OAuth2AuthorizationSession::state)
                .containsExactly(googleState);

        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE,
                        "google-provider-check",
                        "rider@gmail.com",
                        true,
                        "보더");
        Member member =
                Member.builder()
                        .email("rider@gmail.com")
                        .nickname("보더")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        given(
                        oauth2ExternalClient.fetchUserProfile(
                                SocialProvider.GOOGLE, "google-code", googleState))
                .willReturn(profile);
        given(socialAuthService.findMemberForLogin(profile))
                .willReturn(java.util.Optional.of(member));
        MockHttpServletRequest googleCallbackRequest = new MockHttpServletRequest();
        googleCallbackRequest.setSession(session);

        ResponseEntity<Void> googleResponse =
                controller.callback(
                        "google",
                        "google-code",
                        googleState,
                        null,
                        googleCallbackRequest,
                        new MockHttpServletResponse());

        assertThat(googleResponse.getHeaders().getLocation())
                .isEqualTo(URI.create("https://snowthing.org/posts"));
        assertThat(session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("두 탭에서 시작한 OAuth 요청은 서로 덮어쓰지 않고 역순 콜백도 각각 성공한다")
    void authorizeAndCallback_TwoPendingStates_BothSucceedInReverseOrder() {
        given(oauth2ExternalClient.buildAuthorizationUri(eq(SocialProvider.GOOGLE), anyString()))
                .willReturn(URI.create("https://accounts.google.com/o/oauth2/v2/auth"));

        MockHttpServletRequest authorizeRequest = new MockHttpServletRequest();
        controller.authorize("google", "/posts", authorizeRequest);
        controller.authorize("google", "/carpool", authorizeRequest);
        MockHttpSession session = (MockHttpSession) authorizeRequest.getSession(false);
        OAuth2AuthController.OAuth2AuthorizationSessions savedStates =
                (OAuth2AuthController.OAuth2AuthorizationSessions)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE);
        assertThat(savedStates.pending()).hasSize(2);
        OAuth2AuthController.OAuth2AuthorizationSession first = savedStates.pending().get(0);
        OAuth2AuthController.OAuth2AuthorizationSession second = savedStates.pending().get(1);

        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.GOOGLE, "google-777", "rider@gmail.com", true, "보더");
        Member member =
                Member.builder()
                        .email("rider@gmail.com")
                        .nickname("보더")
                        .role(Role.ROLE_USER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        given(
                        oauth2ExternalClient.fetchUserProfile(
                                eq(SocialProvider.GOOGLE), anyString(), anyString()))
                .willReturn(profile);
        given(socialAuthService.findMemberForLogin(profile))
                .willReturn(java.util.Optional.of(member));

        MockHttpServletRequest secondCallbackRequest = new MockHttpServletRequest();
        secondCallbackRequest.setSession(session);
        ResponseEntity<Void> secondResponse =
                controller.callback(
                        "google",
                        "second-code",
                        second.state(),
                        null,
                        secondCallbackRequest,
                        new MockHttpServletResponse());
        assertThat(secondResponse.getHeaders().getLocation())
                .isEqualTo(URI.create("https://snowthing.org/carpool"));

        OAuth2AuthController.OAuth2AuthorizationSessions afterSecond =
                (OAuth2AuthController.OAuth2AuthorizationSessions)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE);
        assertThat(afterSecond.pending())
                .extracting(OAuth2AuthController.OAuth2AuthorizationSession::state)
                .containsExactly(first.state());

        MockHttpServletRequest firstCallbackRequest = new MockHttpServletRequest();
        firstCallbackRequest.setSession(session);
        ResponseEntity<Void> firstResponse =
                controller.callback(
                        "google",
                        "first-code",
                        first.state(),
                        null,
                        firstCallbackRequest,
                        new MockHttpServletResponse());
        assertThat(firstResponse.getHeaders().getLocation())
                .isEqualTo(URI.create("https://snowthing.org/posts"));
        assertThat(session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("세션에는 최근 OAuth 인가 요청을 최대 5개만 보관한다")
    void authorize_MoreThanFiveRequests_KeepsLatestFive() {
        given(oauth2ExternalClient.buildAuthorizationUri(eq(SocialProvider.NAVER), anyString()))
                .willReturn(URI.create("https://nid.naver.com/oauth2.0/authorize"));
        MockHttpServletRequest request = new MockHttpServletRequest();

        for (int index = 0; index < 6; index++) {
            controller.authorize("naver", "/posts/" + index, request);
        }

        MockHttpSession session = (MockHttpSession) request.getSession(false);
        OAuth2AuthController.OAuth2AuthorizationSessions savedStates =
                (OAuth2AuthController.OAuth2AuthorizationSessions)
                        session.getAttribute(OAuth2AuthController.SESSION_STATE_ATTRIBUTE);
        assertThat(savedStates.pending()).hasSize(5);
        assertThat(savedStates.pending())
                .extracting(OAuth2AuthController.OAuth2AuthorizationSession::returnUrl)
                .containsExactly("/posts/1", "/posts/2", "/posts/3", "/posts/4", "/posts/5");
    }

    @Test
    @DisplayName("지원하지 않는 공급자 로그인 요청은 OAUTH_PROVIDER_UNSUPPORTED로 거부된다")
    void authorize_UnknownProvider_RedirectsToUnsupportedProviderError() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Void> response = controller.authorize("apple", null, request);

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
