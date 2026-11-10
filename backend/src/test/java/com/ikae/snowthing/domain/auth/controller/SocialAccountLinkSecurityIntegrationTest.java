package com.ikae.snowthing.domain.auth.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 소셜 계정 연결과 해제가 로그인과 CSRF 토큰을 요구하는지 보안 필터 체인으로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class SocialAccountLinkSecurityIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("로그인하지 않으면 연결을 시작할 수 없다")
    void startLink_Unauthenticated_Returns401() throws Exception {
        mvc.perform(post("/api/v1/auth/oauth2/link/kakao").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 연결을 시작할 수 없다")
    void startLink_WithoutCsrf_Returns403() throws Exception {
        mvc.perform(post("/api/v1/auth/oauth2/link/kakao").with(user("member@snowthing.org")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인하지 않으면 연결을 해제할 수 없다")
    void unlink_Unauthenticated_Returns401() throws Exception {
        mvc.perform(delete("/api/v1/members/me/social-accounts/kakao").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF 토큰이 없으면 연결을 해제할 수 없다")
    void unlink_WithoutCsrf_Returns403() throws Exception {
        mvc.perform(
                        delete("/api/v1/members/me/social-accounts/kakao")
                                .with(user("member@snowthing.org")))
                .andExpect(status().isForbidden());
    }
}
