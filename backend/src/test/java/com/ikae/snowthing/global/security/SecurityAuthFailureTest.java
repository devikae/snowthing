package com.ikae.snowthing.global.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberResortRepository;
import com.ikae.snowthing.domain.member.repository.MemberRidingStyleRepository;
import com.ikae.snowthing.domain.member.service.MemberService;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthFailureTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberResortRepository memberResortRepository;
    @Autowired private MemberRidingStyleRepository memberRidingStyleRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
        MemberSignUpRequest signUpRequest =
                MemberSignUpRequest.builder()
                        .email("authfailuser@snowthing.com")
                        .password("Password123!")
                        .nickname("보안테스터")
                        .build();
        memberService.signUp(signUpRequest);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        memberResortRepository.deleteAll();
        memberRidingStyleRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName(
            "[인증 실패 검증 1] 세션 쿠키 없이 /api/v1/members/me (GET) 접근 시 AuthenticationEntryPoint 가 동작하여 401 Unauthorized 를 반환해야 한다")
    void getMyProfile_WithoutSession_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("[인증 실패 검증 2] 세션 쿠키 없이 /api/v1/members/me (PUT) 접근 시 401 Unauthorized 를 반환해야 한다")
    void updateMyProfile_WithoutSession_Returns401() throws Exception {
        mockMvc.perform(
                        put("/api/v1/members/me")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nickname\":\"새닉네임\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("[인증 실패 검증 3] 무효화(invalidate)되거나 가짜 세션으로 접근 시 401 Unauthorized 로 즉시 차단되어야 한다")
    void request_WithInvalidSession_Returns401() throws Exception {
        MockHttpSession invalidSession = new MockHttpSession();
        invalidSession.invalidate(); // 세션 무효화

        mockMvc.perform(get("/api/v1/members/me").session(invalidSession))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName(
            "[권한 실패 검증 4] 일반 회원(ROLE_USER)이 관리자 URL(/api/v1/admin/**) 접근 시 AccessDeniedHandler 가 동작하여 403 Forbidden 을 반환해야 한다")
    void userAccess_AdminUrl_Returns403() throws Exception {
        // 1. 일반 회원 세션 로그인
        MemberLoginRequest loginRequest =
                MemberLoginRequest.builder()
                        .email("authfailuser@snowthing.com")
                        .password("Password123!")
                        .build();

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginRequest)))
                        .andExpect(status().isOk())
                        .andReturn();

        MockHttpSession userSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        // 2. 일반 회원 세션으로 /api/v1/admin/dashboard 접근 ➔ 403 Forbidden 차단!
        mockMvc.perform(get("/api/v1/admin/dashboard").session(userSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }
}
