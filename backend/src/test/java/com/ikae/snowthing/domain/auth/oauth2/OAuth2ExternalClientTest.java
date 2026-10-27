package com.ikae.snowthing.domain.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class OAuth2ExternalClientTest {

    private MockRestServiceServer mockServer;
    private OAuth2ExternalClient oauth2ExternalClient;

    @BeforeEach
    void setUp() {
        OAuth2Properties properties =
                new OAuth2Properties(
                        "https://snowthing.org",
                        3000,
                        5000,
                        new OAuth2Properties.Provider(
                                "google-client-id",
                                "google-client-secret",
                                "https://snowthing.org/api/v1/auth/oauth2/callback/google"),
                        new OAuth2Properties.Provider(
                                "naver-client-id",
                                "naver-client-secret",
                                "https://snowthing.org/api/v1/auth/oauth2/callback/naver"));

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        oauth2ExternalClient = new OAuth2ExternalClient(properties, builder.build());
    }

    @Test
    @DisplayName("Google, Naver 인가 요청 URI가 필수 쿼리 파라미터를 포함하여 생성된다")
    void buildAuthorizationUri_AllProviders_Success() {
        URI googleUri =
                oauth2ExternalClient.buildAuthorizationUri(SocialProvider.GOOGLE, "state-123");
        URI naverUri =
                oauth2ExternalClient.buildAuthorizationUri(SocialProvider.NAVER, "state-456");
        assertThat(googleUri.toString())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth")
                .contains("client_id=google-client-id")
                .contains("state=state-123");
        assertThat(naverUri.toString())
                .startsWith("https://nid.naver.com/oauth2.0/authorize")
                .contains("client_id=naver-client-id")
                .contains("state=state-456");
    }

    @Test
    @DisplayName("Google 인가 코드로 토큰을 교환하고 UserInfo 응답을 파싱한다")
    void fetchUserProfile_Google_Success() {
        mockServer
                .expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.allOf(
                                                org.hamcrest.Matchers.containsString(
                                                        "grant_type=authorization_code"),
                                                org.hamcrest.Matchers.containsString(
                                                        "client_id=google-client-id"),
                                                org.hamcrest.Matchers.containsString(
                                                        "code=google-code"))))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"google-access-token\"}",
                                MediaType.APPLICATION_JSON));

        mockServer
                .expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer google-access-token"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "sub": "google-user-999",
                                  "email": "rider@gmail.com",
                                  "email_verified": true,
                                  "name": "구글보더"
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        OAuthUserProfile profile =
                oauth2ExternalClient.fetchUserProfile(
                        SocialProvider.GOOGLE, "google-code", "state-1");

        assertThat(profile.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(profile.providerUserId()).isEqualTo("google-user-999");
        assertThat(profile.email()).isEqualTo("rider@gmail.com");
        assertThat(profile.emailAuthoritative()).isTrue();
        assertThat(profile.nickname()).isEqualTo("구글보더");
        mockServer.verify();
    }

    @Test
    @DisplayName("Naver 인가 코드로 토큰을 교환하고 UserInfo 응답을 파싱한다")
    void fetchUserProfile_Naver_Success() {
        mockServer
                .expect(requestTo("https://nid.naver.com/oauth2.0/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.allOf(
                                                org.hamcrest.Matchers.containsString(
                                                        "client_id=naver-client-id"),
                                                org.hamcrest.Matchers.containsString(
                                                        "code=naver-code"),
                                                org.hamcrest.Matchers.containsString(
                                                        "state=naver-state"))))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"naver-access-token\"}",
                                MediaType.APPLICATION_JSON));

        mockServer
                .expect(requestTo("https://openapi.naver.com/v1/nid/me"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer naver-access-token"))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "resultcode": "00",
                                  "message": "success",
                                  "response": {
                                    "id": "naver-user-777",
                                    "email": "rider@naver.com",
                                    "nickname": "네이버스키어"
                                  }
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        OAuthUserProfile profile =
                oauth2ExternalClient.fetchUserProfile(
                        SocialProvider.NAVER, "naver-code", "naver-state");

        assertThat(profile.provider()).isEqualTo(SocialProvider.NAVER);
        assertThat(profile.providerUserId()).isEqualTo("naver-user-777");
        assertThat(profile.email()).isEqualTo("rider@naver.com");
        assertThat(profile.emailAuthoritative()).isFalse();
        assertThat(profile.nickname()).isEqualTo("네이버스키어");
        mockServer.verify();
    }

    @Test
    @DisplayName("Google 외부 도메인은 email_verified가 true여도 현재 소유권의 권위 있는 증명으로 취급하지 않는다")
    void fetchUserProfile_GoogleExternalEmail_IsNotAuthoritative() {
        mockServer
                .expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"google-access-token\"}",
                                MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "sub": "google-external-1",
                                  "email": "rider@example.com",
                                  "email_verified": true,
                                  "name": "외부메일"
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        OAuthUserProfile profile =
                oauth2ExternalClient.fetchUserProfile(
                        SocialProvider.GOOGLE, "google-code", "state-external");

        assertThat(profile.emailAuthoritative()).isFalse();
        mockServer.verify();
    }

    @Test
    @DisplayName("Google Workspace 이메일은 email_verified와 hd가 모두 있으면 권위 있는 이메일로 취급한다")
    void fetchUserProfile_GoogleWorkspaceEmail_IsAuthoritative() {
        mockServer
                .expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withSuccess(
                                "{\"access_token\":\"workspace-access-token\"}",
                                MediaType.APPLICATION_JSON));
        mockServer
                .expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "sub": "google-workspace-1",
                                  "email": "rider@snowthing.org",
                                  "email_verified": true,
                                  "hd": "snowthing.org",
                                  "name": "워크스페이스"
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        OAuthUserProfile profile =
                oauth2ExternalClient.fetchUserProfile(
                        SocialProvider.GOOGLE, "workspace-code", "workspace-state");

        assertThat(profile.emailAuthoritative()).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("클라이언트 설정이 비어 있는 공급자 호출 시 OAUTH_PROVIDER_NOT_CONFIGURED 예외가 발생한다")
    void buildAuthorizationUri_NotConfigured_ThrowsException() {
        OAuth2Properties emptyProperties =
                new OAuth2Properties("http://localhost:3000", 3000, 5000, null, null);
        OAuth2ExternalClient unconfiguredClient =
                new OAuth2ExternalClient(emptyProperties, RestClient.create());

        assertThatThrownBy(
                        () ->
                                unconfiguredClient.buildAuthorizationUri(
                                        SocialProvider.GOOGLE, "state"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED);
    }

    @Test
    @DisplayName("외부 토큰 서버가 400 에러를 반환하면 OAUTH_AUTHENTICATION_FAILED 예외로 변환된다")
    void fetchUserProfile_TokenEndpointFailure_ThrowsOAuthAuthenticationFailed() {
        mockServer
                .expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest());

        assertThatThrownBy(
                        () ->
                                oauth2ExternalClient.fetchUserProfile(
                                        SocialProvider.GOOGLE, "bad-code", "state"))
                .isInstanceOf(CustomException.class)
                .extracting(ex -> ((CustomException) ex).getErrorCode())
                .isEqualTo(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
    }
}
