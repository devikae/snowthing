package com.ikae.snowthing.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        MemberSignUpRequest signUpRequest = MemberSignUpRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("Password123!")
                .nickname("세션보더")
                .build();
        memberService.signUp(signUpRequest);
    }

    @AfterEach
    void tearDown() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("[검증 1] 올바른 로그인 요청 시 200 OK와 함께 회원 프로필이 반환되어야 한다")
    void login_Success() throws Exception {
        MemberLoginRequest loginRequest = MemberLoginRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").exists())
                .andExpect(jsonPath("$.email").value("sessionuser@snowthing.com"))
                .andExpect(jsonPath("$.nickname").value("세션보더"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("[검증 2] 비밀번호가 일치하지 않는 경우 401 Unauthorized 가 반환되어야 한다")
    void login_InvalidPassword_Returns401() throws Exception {
        MemberLoginRequest loginRequest = MemberLoginRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    @DisplayName("[검증 3] 로그인 시 changeSessionId() 가 호출되어 세션 ID가 새로 재발급(세션 고정 방어)되어야 한다")
    void login_ChangeSessionId_SessionFixationProtection() throws Exception {
        String oldSessionId = "BEFORE_LOGIN_SESSION_ID_9999";
        MockHttpSession beforeSession = new MockHttpSession(null, oldSessionId);

        MemberLoginRequest loginRequest = MemberLoginRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("Password123!")
                .build();

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .session(beforeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession afterSession = (MockHttpSession) mvcResult.getRequest().getSession();
        assertThat(afterSession).isNotNull();
        String afterSessionId = afterSession.getId();

        assertThat(afterSessionId).isNotEqualTo(oldSessionId);
    }

    @Test
    @DisplayName("[검증 4] 로그인 후 /api/v1/members/me 접근 성공 및 로그아웃 후 세션 무효화로 접근 차단(401) 실증")
    void login_Me_And_Logout_SessionInvalidate_Success() throws Exception {
        MockHttpSession beforeSession = new MockHttpSession();

        MemberLoginRequest loginRequest = MemberLoginRequest.builder()
                .email("sessionuser@snowthing.com")
                .password("Password123!")
                .build();

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login").with(csrf())
                        .session(beforeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("sessionuser@snowthing.com"));

        mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("LOGOUT_SUCCESS"));

        MockHttpSession emptySession = new MockHttpSession();
        mockMvc.perform(get("/api/v1/members/me").session(emptySession))
                .andExpect(status().isUnauthorized());
    }
}
