package com.ikae.snowthing.domain.auth.oauth2;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.ikae.snowthing.domain.member.entity.SocialProvider;

@ConfigurationProperties(prefix = "snowthing.oauth2")
public record OAuth2Properties(
        String frontendBaseUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        Provider google,
        Provider naver) {

    private static final String DEFAULT_FRONTEND_BASE_URL = "http://localhost:3000";
    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int DEFAULT_READ_TIMEOUT_MILLIS = 4000;

    public record Provider(String clientId, String clientSecret, String redirectUri) {

        public boolean hasClientIdAndRedirectUri() {
            return isNonBlank(clientId) && isNonBlank(redirectUri);
        }

        public boolean hasClientSecret() {
            return isNonBlank(clientSecret);
        }

        private static boolean isNonBlank(String value) {
            return value != null && !value.isBlank();
        }
    }

    public String effectiveFrontendBaseUrl() {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            return DEFAULT_FRONTEND_BASE_URL;
        }
        String trimmed = frontendBaseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public int effectiveConnectTimeoutMillis() {
        return connectTimeoutMillis > 0 ? connectTimeoutMillis : DEFAULT_CONNECT_TIMEOUT_MILLIS;
    }

    public int effectiveReadTimeoutMillis() {
        return readTimeoutMillis > 0 ? readTimeoutMillis : DEFAULT_READ_TIMEOUT_MILLIS;
    }

    public Provider providerConfig(SocialProvider provider) {
        return switch (provider) {
            case GOOGLE -> google;
            case NAVER -> naver;
        };
    }

    public boolean isConfigured(SocialProvider provider) {
        Provider config = providerConfig(provider);
        if (config == null || !config.hasClientIdAndRedirectUri()) {
            return false;
        }
        return config.hasClientSecret();
    }
}
