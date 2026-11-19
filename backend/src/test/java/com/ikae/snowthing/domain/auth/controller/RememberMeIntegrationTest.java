package com.ikae.snowthing.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberResortRepository;
import com.ikae.snowthing.domain.member.repository.MemberRidingStyleRepository;
import com.ikae.snowthing.domain.member.service.MemberService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.security.MemberSessionRegistry;
import com.ikae.snowthing.global.security.RememberMeTokenRepository;
import com.ikae.snowthing.global.security.SnowthingRememberMeServices;

/** 세션이 사라져도 로그인 유지 쿠키로 다시 인증되는지, 끝낼 때 토큰이 지워지는지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class RememberMeIntegrationTest {

    private static final String EMAIL = "remember@snowthing.com";
    private static final String PASSWORD = "Password123!";
    private static final String COOKIE = SnowthingRememberMeServices.COOKIE_NAME;
    private static final String ME = "/api/v1/members/me/settings";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberResortRepository memberResortRepository;
    @Autowired private MemberRidingStyleRepository memberRidingStyleRepository;
    @Autowired private MemberSessionRegistry memberSessionRegistry;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SnowthingRememberMeServices rememberMeServices;
    @Autowired private RememberMeTokenRepository rememberMeTokenRepository;
    @Autowired private Clock clock;

    @BeforeEach
    void setUp() {
        cleanUp();
        memberService.signUp(
                MemberSignUpRequest.builder()
                        .email(EMAIL)
                        .password(PASSWORD)
                        .nickname("유지보더")
                        .build());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM persistent_logins");
        memberResortRepository.deleteAll();
        memberRidingStyleRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인하면 30일짜리 HttpOnly, SameSite=Lax 로그인 유지 쿠키를 내려주고 토큰을 저장한다")
    void login_IssuesRememberMeCookie() throws Exception {
        MvcResult result = login();

        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getMaxAge()).isEqualTo((int) Duration.ofDays(30).toSeconds());
        // MockHttpServletResponse는 SameSite를 헤더에 쓰지 않는다. Tomcat은 쿠키 속성을 그대로 헤더에 쓴다.
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(tokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("세션 없이 쿠키만 있어도 인증되고, 토큰을 바꿔 새 쿠키를 내려준다")
    void cookieOnly_AuthenticatesAndRotates() throws Exception {
        Cookie issued = login().getResponse().getCookie(COOKIE);
        String hashBefore = tokenHash();

        MvcResult result =
                mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isOk()).andReturn();

        Cookie rotated = result.getResponse().getCookie(COOKIE);
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(issued.getValue());
        assertThat(tokenHash()).isNotEqualTo(hashBefore);
        // 새로 만든 세션이 다음 요청부터 인증을 이어간다.
        assertThat(result.getRequest().getSession(false)).isNotNull();
        mockMvc.perform(get(ME).cookie(rotated)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("동시에 출발한 요청이 바뀌기 전 쿠키를 내밀어도 잠깐은 받아 준다")
    void previousCookieWithinGrace_IsAccepted() throws Exception {
        Cookie issued = login().getResponse().getCookie(COOKIE);
        mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isOk());

        mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isOk());
        assertThat(tokenCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("바뀐 지 오래된 쿠키가 들어오면 도난으로 보고 그 회원의 토큰을 모두 지운다")
    void staleCookieAfterGrace_DeletesAllTokens() throws Exception {
        Cookie issued = login().getResponse().getCookie(COOKIE);
        Cookie rotated =
                mockMvc.perform(get(ME).cookie(issued))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getCookie(COOKIE);
        jdbc.update(
                "UPDATE persistent_logins SET last_used_at = ?",
                LocalDateTime.now().minusMinutes(5));

        mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isUnauthorized());

        assertThat(tokenCount()).isZero();
        mockMvc.perform(get(ME).cookie(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("30일 넘게 쓰지 않은 쿠키는 인증하지 않는다")
    void expiredCookie_IsRejected() throws Exception {
        Cookie issued = login().getResponse().getCookie(COOKIE);
        jdbc.update(
                "UPDATE persistent_logins SET last_used_at = ?", LocalDateTime.now().minusDays(31));

        mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isUnauthorized());
        assertThat(tokenCount()).isZero();
    }

    @Test
    @DisplayName("로그아웃하면 이 기기의 토큰을 지우고 쿠키를 없앤다")
    void logout_DeletesTokenAndCookie() throws Exception {
        Cookie issued = login().getResponse().getCookie(COOKIE);

        MvcResult result =
                mockMvc.perform(post("/api/v1/auth/logout").with(csrf()).cookie(issued))
                        .andExpect(status().isOk())
                        .andReturn();

        Cookie cleared = result.getResponse().getCookie(COOKIE);
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(tokenCount()).isZero();
        mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("정지된 회원은 토큰이 남아 있어도 쿠키로 다시 로그인하지 못한다")
    void suspendedMember_IsNotAutoLoggedIn() throws Exception {
        Cookie issued = login().getResponse().getCookie(COOKIE);
        Member member = memberRepository.findByEmail(EMAIL).orElseThrow();
        member.suspend();
        memberRepository.save(member);

        mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자 정지, 비밀번호 변경처럼 회원 세션을 모두 끊으면 로그인 유지 토큰도 지운다")
    void invalidateAll_DeletesTokens() throws Exception {
        Cookie issued = login().getResponse().getCookie(COOKIE);
        String publicId = memberRepository.findByEmail(EMAIL).orElseThrow().getPublicId();

        memberSessionRegistry.invalidateAll(publicId);

        assertThat(tokenCount()).isZero();
        mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관리자는 로그인해도 로그인 유지 쿠키와 토큰을 만들지 않는다")
    void adminLogin_DoesNotIssueRememberMe() throws Exception {
        Member member = memberRepository.findByEmail(EMAIL).orElseThrow();
        member.changeRole(Role.ROLE_ADMIN);
        memberRepository.save(member);

        MvcResult result = login();

        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie == null || cookie.getMaxAge() == 0).isTrue();
        assertThat(tokenCount()).isZero();
    }

    @Test
    @DisplayName("일반 회원 때 받은 토큰이 남아 있어도 관리자가 되면 쿠키로 다시 로그인하지 못한다")
    void tokenIssuedBeforeAdmin_IsRejected() throws Exception {
        Cookie issued = login().getResponse().getCookie(COOKIE);
        // 권한 변경 경로는 토큰을 지우므로, 지우지 못한 경우를 흉내 내려고 엔티티만 바꾼다.
        Member member = memberRepository.findByEmail(EMAIL).orElseThrow();
        member.changeRole(Role.ROLE_ADMIN);
        memberRepository.save(member);

        mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isUnauthorized());
        assertThat(tokenCount()).isZero();
    }

    @Test
    @DisplayName("도난으로 보면 훔친 쿠키로 이미 만든 세션도 함께 끊는다")
    void theftDetection_InvalidatesSessionsMadeFromStolenCookie() throws Exception {
        Cookie issued = login().getResponse().getCookie(COOKIE);
        // 공격자가 먼저 쿠키를 써서 세션을 얻고 token을 바꾼다.
        MockHttpSession attackerSession =
                (MockHttpSession)
                        mockMvc.perform(get(ME).cookie(issued))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getRequest()
                                .getSession(false);
        jdbc.update(
                "UPDATE persistent_logins SET last_used_at = ?",
                LocalDateTime.now().minusMinutes(5));

        // 원래 주인이 바뀌기 전 쿠키로 돌아온다.
        mockMvc.perform(get(ME).cookie(issued)).andExpect(status().isUnauthorized());

        assertThat(attackerSession).isNotNull();
        assertThat(attackerSession.isInvalid()).isTrue();
        assertThat(tokenCount()).isZero();
    }

    private MvcResult login() throws Exception {
        MemberLoginRequest request =
                MemberLoginRequest.builder().email(EMAIL).password(PASSWORD).build();
        return mockMvc.perform(
                        post("/api/v1/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private int tokenCount() {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM persistent_logins", Integer.class);
        return count == null ? 0 : count;
    }

    private String tokenHash() {
        return jdbc.queryForObject("SELECT token_hash FROM persistent_logins", String.class);
    }
}
