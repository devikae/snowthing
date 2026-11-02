package com.ikae.snowthing.domain.auth.oauth2;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import tools.jackson.databind.JsonNode;

@Component
public class OAuth2ExternalClient {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USER_INFO_URL =
            "https://openidconnect.googleapis.com/v1/userinfo";
    private static final String GOOGLE_SCOPE = "openid email profile";

    private static final String NAVER_AUTH_URL = "https://nid.naver.com/oauth2.0/authorize";
    private static final String NAVER_TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String NAVER_USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    private final OAuth2Properties properties;
    private final RestClient restClient;

    @Autowired
    public OAuth2ExternalClient(OAuth2Properties properties) {
        this(properties, createRestClient(properties));
    }

    OAuth2ExternalClient(OAuth2Properties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    public URI buildAuthorizationUri(SocialProvider provider, String state) {
        OAuth2Properties.Provider config = requireConfigured(provider);
        return switch (provider) {
            case GOOGLE ->
                    UriComponentsBuilder.fromUriString(GOOGLE_AUTH_URL)
                            .queryParam("response_type", "code")
                            .queryParam("client_id", config.clientId())
                            .queryParam("redirect_uri", config.redirectUri())
                            .queryParam("scope", GOOGLE_SCOPE)
                            .queryParam("state", state)
                            .encode()
                            .build()
                            .toUri();
            case NAVER ->
                    UriComponentsBuilder.fromUriString(NAVER_AUTH_URL)
                            .queryParam("response_type", "code")
                            .queryParam("client_id", config.clientId())
                            .queryParam("redirect_uri", config.redirectUri())
                            .queryParam("state", state)
                            .encode()
                            .build()
                            .toUri();
        };
    }

    public OAuthUserProfile fetchUserProfile(SocialProvider provider, String code, String state) {
        if (code == null || code.isBlank()) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        OAuth2Properties.Provider config = requireConfigured(provider);
        String accessToken = requestAccessToken(provider, config, code, state);
        return requestUserProfile(provider, accessToken);
    }

    private String requestAccessToken(
            SocialProvider provider, OAuth2Properties.Provider config, String code, String state) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", config.clientId());
        formData.add("redirect_uri", config.redirectUri());
        formData.add("code", code);
        if (config.hasClientSecret()) {
            formData.add("client_secret", config.clientSecret());
        }
        if (provider == SocialProvider.NAVER && state != null && !state.isBlank()) {
            formData.add("state", state);
        }

        String tokenUrl =
                switch (provider) {
                    case GOOGLE -> GOOGLE_TOKEN_URL;
                    case NAVER -> NAVER_TOKEN_URL;
                };

        try {
            JsonNode response =
                    restClient
                            .post()
                            .uri(tokenUrl)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(formData)
                            .retrieve()
                            .body(JsonNode.class);
            if (response == null) {
                throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
            }
            String accessToken = response.path("access_token").asText("");
            if (accessToken.isBlank()) {
                throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
            }
            return accessToken;
        } catch (CustomException exception) {
            throw exception;
        } catch (RestClientException | IllegalStateException exception) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED, exception);
        }
    }

    private OAuthUserProfile requestUserProfile(SocialProvider provider, String accessToken) {
        String userInfoUrl =
                switch (provider) {
                    case GOOGLE -> GOOGLE_USER_INFO_URL;
                    case NAVER -> NAVER_USER_INFO_URL;
                };
        try {
            JsonNode response =
                    restClient
                            .get()
                            .uri(userInfoUrl)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .retrieve()
                            .body(JsonNode.class);
            if (response == null) {
                throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
            }
            return switch (provider) {
                case GOOGLE -> parseGoogleProfile(response);
                case NAVER -> parseNaverProfile(response);
            };
        } catch (CustomException exception) {
            throw exception;
        } catch (RestClientException | IllegalStateException exception) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED, exception);
        }
    }

    private OAuthUserProfile parseGoogleProfile(JsonNode root) {
        String sub = root.path("sub").asText("").trim();
        if (sub.isBlank()) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        String email = nullIfBlank(root.path("email").asText(null));
        boolean emailAuthoritative =
                root.path("email_verified").asBoolean(false)
                        && isGoogleAuthoritativeEmail(email, root.path("hd").asText(null));
        String name = nullIfBlank(root.path("name").asText(null));
        return new OAuthUserProfile(SocialProvider.GOOGLE, sub, email, emailAuthoritative, name);
    }

    private OAuthUserProfile parseNaverProfile(JsonNode root) {
        String resultCode = root.path("resultcode").asText("");
        JsonNode response = root.path("response");
        String id = response.path("id").asText("").trim();
        if (!"00".equals(resultCode) || id.isBlank()) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        String email = nullIfBlank(response.path("email").asText(null));
        String nickname =
                nullIfBlank(response.path("nickname").asText(response.path("name").asText(null)));
        return new OAuthUserProfile(SocialProvider.NAVER, id, email, false, nickname);
    }

    private OAuth2Properties.Provider requireConfigured(SocialProvider provider) {
        if (!properties.isConfigured(provider)) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_NOT_CONFIGURED);
        }
        return properties.providerConfig(provider);
    }

    private static String nullIfBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean isGoogleAuthoritativeEmail(String email, String hostedDomain) {
        if (email == null) {
            return false;
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        return normalizedEmail.endsWith("@gmail.com") || nullIfBlank(hostedDomain) != null;
    }

    private static RestClient createRestClient(OAuth2Properties properties) {
        HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofMillis(properties.effectiveConnectTimeoutMillis()))
                        .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.effectiveReadTimeoutMillis()));
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
