package com.ikae.snowthing.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import com.ikae.snowthing.global.config.websocket.ChatConnectionRegistry;
import com.ikae.snowthing.global.config.websocket.ChatSessionRevocationRegistry;
import com.ikae.snowthing.global.security.MemberSessionRegistry;

class SecurityConfigWebSettingsTest {

    private SecurityConfig config(List<String> origins, boolean secureCookies) {
        SecurityConfig config =
                new SecurityConfig(
                        mock(ChatSessionRevocationRegistry.class),
                        mock(ChatConnectionRegistry.class),
                        mock(MemberSessionRegistry.class));
        ReflectionTestUtils.setField(config, "allowedOrigins", origins);
        ReflectionTestUtils.setField(config, "secureCookies", secureCookies);
        return config;
    }

    @Test
    @DisplayName("운영 출처 목록에는 localhost가 없어 CORS로 막힌다")
    void prodOriginsRejectLocalhost() {
        SecurityConfig config =
                config(List.of("https://snowthing.org", "https://www.snowthing.org"), true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/posts");
        CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.checkOrigin("https://snowthing.org")).isEqualTo("https://snowthing.org");
        assertThat(cors.checkOrigin("http://localhost:3000")).isNull();
    }

    @Test
    @DisplayName("운영에서는 CSRF 쿠키에 Secure가 붙고, 로컬에서는 붙지 않는다")
    void csrfCookieSecureFollowsSetting() {
        assertThat(csrfSetCookie(config(List.of(), true))).contains("Secure");
        assertThat(csrfSetCookie(config(List.of(), false))).doesNotContain("Secure");
    }

    private String csrfSetCookie(SecurityConfig config) {
        CookieCsrfTokenRepository repository =
                ReflectionTestUtils.invokeMethod(config, "csrfTokenRepository");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        repository.saveToken(repository.generateToken(request), request, response);
        return response.getHeader("Set-Cookie");
    }
}
