package com.ikae.snowthing.domain.auth.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.security.CustomUserDetails;

/** 소셜 계정 연결과 해제가 로그인과 CSRF 토큰을 요구하는지 보안 필터 체인으로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class SocialAccountLinkSecurityIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MemberRepository members;
    @Autowired PasswordEncoder passwordEncoder;

    private final List<Member> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        members.deleteAll(created);
    }

    @Test
    @DisplayName("비밀번호 회원이 현재 비밀번호를 틀리면 연결을 시작하지 않는다")
    void startLink_WrongPassword_Returns400() throws Exception {
        CustomUserDetails member = new CustomUserDetails(save("Password123!"));

        mvc.perform(
                        post("/api/v1/auth/oauth2/link/kakao")
                                .with(csrf())
                                .with(user(member))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"currentPassword\":\"WrongPassword999!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_004"));
        mvc.perform(post("/api/v1/auth/oauth2/link/kakao").with(csrf()).with(user(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MEMBER_004"));
    }

    @Test
    @DisplayName("소셜 전용 회원은 이 세션에서 최근에 로그인한 기록이 없으면 다시 로그인하라고 거부한다")
    void startLink_SocialOnlyWithoutRecentLogin_Returns403() throws Exception {
        CustomUserDetails member = new CustomUserDetails(save(null));

        mvc.perform(post("/api/v1/auth/oauth2/link/kakao").with(csrf()).with(user(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_021"));
    }

    private Member save(String rawPassword) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member =
                members.saveAndFlush(
                        Member.builder()
                                .email("link-" + suffix + "@example.com")
                                .password(
                                        rawPassword == null
                                                ? null
                                                : passwordEncoder.encode(rawPassword))
                                .nickname("연결" + suffix)
                                .role(Role.ROLE_USER)
                                .build());
        created.add(member);
        return member;
    }

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
