package com.ikae.snowthing.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.apitoken.dto.ApiTokenCreateRequest;
import com.ikae.snowthing.domain.apitoken.service.ApiTokenService;
import com.ikae.snowthing.domain.auth.controller.AuthController;
import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.auth.service.SocialAuthService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberSocialAccount;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberSocialAccountRepository;
import com.ikae.snowthing.domain.member.service.AdminMemberService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.MemberSessionRegistry;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** 관리자 회원 조회와 정지 통합 테스트 (IKA-42). 실제 로그인으로 세션을 만들어 정지 전후의 접근을 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AdminMemberIntegrationTest {

    private static final String MEMBERS = "/api/v1/admin/members";
    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberRepository members;
    @Autowired private MemberSocialAccountRepository socialAccounts;
    @Autowired private ApiTokenService apiTokenService;
    @Autowired private AdminMemberService adminMemberService;

    @Autowired private MemberSessionRegistry memberSessionRegistry;
    @Autowired private AuthController authController;
    @Autowired private SocialAuthService socialAuthService;

    private final List<Long> memberIds = new ArrayList<>();
    private final ListAppender<ILoggingEvent> auditLog = new ListAppender<>();
    private Member admin;
    private Member otherAdmin;
    private Member target;
    private MockHttpSession adminSession;

    @BeforeEach
    void setUp() throws Exception {
        auditLog.start();
        ((Logger) LoggerFactory.getLogger("admin.audit")).addAppender(auditLog);
        admin = member("admin", Role.ROLE_ADMIN, MemberStatus.ACTIVE);
        otherAdmin = member("admin2", Role.ROLE_ADMIN, MemberStatus.ACTIVE);
        target = member("target", Role.ROLE_USER, MemberStatus.ACTIVE);
        adminSession = login(admin);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger("admin.audit")).detachAppender(auditLog);
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM api_token WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM member_social_account WHERE member_id = ?", memberId);
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("비관리자는 403, 비로그인은 401, CSRF 토큰이 없으면 관리자도 403이다")
    void requiresAdminAndCsrf() throws Exception {
        MockHttpSession userSession = login(target);
        Member other = member("other", Role.ROLE_USER, MemberStatus.ACTIVE);

        mockMvc.perform(get(MEMBERS).session(userSession)).andExpect(status().isForbidden());
        mockMvc.perform(suspendRequest(other).session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(MEMBERS)).andExpect(status().isUnauthorized());
        mockMvc.perform(suspendRequest(other).session(adminSession))
                .andExpect(status().isForbidden());

        assertThat(statusOf(other)).isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("닉네임과 이메일로 찾고, 민감값 없이 내보내며, 가입일이 같으면 ID 역순이다")
    void searchesWithoutSensitiveValues() throws Exception {
        String keyword = "srch" + UUID.randomUUID().toString().substring(0, 8);
        Member first = member(keyword + "a", Role.ROLE_USER, MemberStatus.ACTIVE);
        Member second = member(keyword + "b", Role.ROLE_USER, MemberStatus.ACTIVE);
        LocalDateTime sameTime = LocalDateTime.of(2026, 1, 1, 0, 0);
        jdbc.update(
                "UPDATE member SET created_at = ? WHERE member_id IN (?, ?)",
                sameTime,
                first.getId(),
                second.getId());

        MvcResult result =
                mockMvc.perform(get(MEMBERS).param("query", keyword).session(adminSession))
                        .andExpect(status().isOk())
                        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                        .andExpect(jsonPath("$.hasNext").value(false))
                        .andReturn();
        JsonNode items = readBody(result).get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("publicId").asText()).isEqualTo(second.getPublicId());
        assertThat(items.get(1).get("publicId").asText()).isEqualTo(first.getPublicId());
        assertThat(items.get(0).has("password")).isFalse();
        assertThat(items.get(0).has("id")).isFalse();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(second.getPassword());

        mockMvc.perform(get(MEMBERS).param("query", first.getEmail()).session(adminSession))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].email").value(first.getEmail()));
        // LIKE 특수문자는 글자로 찾는다.
        mockMvc.perform(get(MEMBERS).param("query", "%" + keyword).session(adminSession))
                .andExpect(jsonPath("$.items.length()").value(0));
        // 페이지 크기 제한
        mockMvc.perform(get(MEMBERS).param("size", "101").session(adminSession))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        get(MEMBERS)
                                .param("query", keyword)
                                .param("size", "1")
                                .session(adminSession))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("정지하면 모든 기존 세션이 끊기고, 쪽지도 못 보내고, 새 로그인은 계정 상태를 드러내지 않고 거부된다")
    void suspendInvalidatesSessionsAndRejectsLogin() throws Exception {
        MockHttpSession firstDevice = login(target);
        MockHttpSession secondDevice = login(target);
        mockMvc.perform(get("/api/v1/members/me").session(firstDevice)).andExpect(status().isOk());

        mockMvc.perform(suspendRequest(target).session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(statusOf(target)).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(firstDevice.isInvalid()).isTrue();
        assertThat(secondDevice.isInvalid()).isTrue();
        mockMvc.perform(get("/api/v1/members/me").session(firstDevice))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/v1/messages")
                                .session(secondDevice)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"receiverPublicId\":\""
                                                + admin.getPublicId()
                                                + "\",\"content\":\"정지 중 쪽지\"}"))
                .andExpect(status().isUnauthorized());
        // 잠김 검사는 비밀번호 검사보다 먼저라 계정 상태를 드러내지 않는다 (IKA-40).
        loginRequest(target)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("해제하면 다시 로그인되지만 정지 때 끊긴 세션은 되살아나지 않는다")
    void reinstateAllowsLoginAgain() throws Exception {
        MockHttpSession oldSession = login(target);
        mockMvc.perform(suspendRequest(target).session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post(MEMBERS + "/{id}/reinstate", target.getPublicId())
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(statusOf(target)).isEqualTo(MemberStatus.ACTIVE);
        mockMvc.perform(get("/api/v1/members/me").session(oldSession))
                .andExpect(status().isUnauthorized());
        MockHttpSession newSession = login(target);
        mockMvc.perform(get("/api/v1/members/me").session(newSession)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("소셜 로그인도 정지 중에는 거부되고 해제하면 된다")
    void socialLoginFollowsSuspension() throws Exception {
        Member social = member("social", Role.ROLE_USER, MemberStatus.ACTIVE);
        String providerUserId = "naver-" + UUID.randomUUID();
        socialAccounts.save(
                MemberSocialAccount.builder()
                        .member(social)
                        .provider(SocialProvider.NAVER)
                        .providerUserId(providerUserId)
                        .providerEmail(social.getEmail())
                        .connectedAt(LocalDateTime.now())
                        .build());
        OAuthUserProfile profile =
                new OAuthUserProfile(
                        SocialProvider.NAVER,
                        providerUserId,
                        social.getEmail(),
                        true,
                        social.getNickname());

        mockMvc.perform(suspendRequest(social).session(adminSession).with(csrf()))
                .andExpect(status().isOk());
        assertThatThrownBy(() -> socialAuthService.findMemberForLogin(profile))
                .isInstanceOf(CustomException.class)
                .extracting(thrown -> ((CustomException) thrown).getErrorCode())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        mockMvc.perform(
                        post(MEMBERS + "/{id}/reinstate", social.getPublicId())
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isOk());
        assertThat(socialAuthService.findMemberForLogin(profile).orElseThrow().getId())
                .isEqualTo(social.getId());
    }

    @Test
    @DisplayName("인증 뒤 세션 등록 전에 정지되어도 세션이 남지 않는다")
    void suspensionDuringLoginDoesNotLeaveSession() throws Exception {
        // 빈을 교체하면 스프링 컨텍스트가 하나 더 떠서 이 필드만 잠시 바꾼다.
        MemberSessionRegistry registry = spy(memberSessionRegistry);
        doAnswer(
                        invocation -> {
                            adminMemberService.suspend(admin.getPublicId(), target.getPublicId());
                            return invocation.callRealMethod();
                        })
                .when(registry)
                .register(eq(target.getPublicId()), any());
        ReflectionTestUtils.setField(authController, "memberSessionRegistry", registry);
        try {
            MvcResult result =
                    loginRequest(target)
                            .andExpect(status().isForbidden())
                            .andExpect(jsonPath("$.code").value("AUTH_009"))
                            .andReturn();

            assertThat(result.getRequest().getSession(false)).isNull();
        } finally {
            ReflectionTestUtils.setField(
                    authController, "memberSessionRegistry", memberSessionRegistry);
        }
    }

    @Test
    @DisplayName("자신, 다른 관리자, 탈퇴 회원은 정지할 수 없고 탈퇴 회원은 해제도 안 된다")
    void rejectsForbiddenTargets() throws Exception {
        Member withdrawn = member("gone", Role.ROLE_USER, MemberStatus.WITHDRAWN);

        mockMvc.perform(suspendRequest(admin).session(adminSession).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_010"));
        mockMvc.perform(suspendRequest(otherAdmin).session(adminSession).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER_011"));
        mockMvc.perform(suspendRequest(withdrawn).session(adminSession).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_012"));
        mockMvc.perform(
                        post(MEMBERS + "/{id}/reinstate", withdrawn.getPublicId())
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(
                        post(MEMBERS + "/{id}/suspend", UUID.randomUUID().toString())
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(statusOf(admin)).isEqualTo(MemberStatus.ACTIVE);
        assertThat(statusOf(otherAdmin)).isEqualTo(MemberStatus.ACTIVE);
        assertThat(statusOf(withdrawn)).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(auditLog.list).isEmpty();
    }

    @Test
    @DisplayName("이 API로는 탈퇴 상태로 바꿀 수 없다")
    void cannotChangeToWithdrawn() throws Exception {
        int patched =
                mockMvc.perform(
                                patch(MEMBERS + "/{id}/status", target.getPublicId())
                                        .session(adminSession)
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"status\":\"WITHDRAWN\"}"))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        assertThat(patched).isGreaterThanOrEqualTo(400);

        mockMvc.perform(
                        suspendRequest(target)
                                .session(adminSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"WITHDRAWN\"}"))
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(statusOf(target)).isEqualTo(MemberStatus.SUSPENDED);
    }

    @Test
    @DisplayName("API 토큰은 소유자가 정지되면 바로 거부되고, 회원 관리 API에는 애초에 못 들어온다")
    void apiTokenFollowsOwnerStatus() throws Exception {
        String token =
                apiTokenService
                        .create(
                                otherAdmin.getId(),
                                new ApiTokenCreateRequest("t", List.of("READ"), 1))
                        .token();
        String evidence = "/api/v1/admin/search/evidence/none";
        int before =
                mockMvc.perform(get(evidence).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                        .andReturn()
                        .getResponse()
                        .getStatus();
        assertThat(before).isNotEqualTo(401);
        mockMvc.perform(get(MEMBERS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        // 관리자는 API로 정지할 수 없으므로 DB에서 상태만 바꿔 확인한다.
        jdbc.update(
                "UPDATE member SET status = 'SUSPENDED' WHERE member_id = ?", otherAdmin.getId());

        mockMvc.perform(get(evidence).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("상태가 바뀔 때만 감사 로그를 남기고 이메일은 넣지 않는다")
    void writesAuditLogOnChangeOnly() throws Exception {
        mockMvc.perform(suspendRequest(target).session(adminSession).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(suspendRequest(target).session(adminSession).with(csrf()))
                .andExpect(status().isOk());

        assertThat(auditLog.list).hasSize(1);
        assertThat(auditLog.list.get(0).getFormattedMessage())
                .contains("action=SUSPEND")
                .contains("actor=" + admin.getPublicId())
                .contains("target=" + target.getPublicId())
                .contains("from=ACTIVE")
                .contains("to=SUSPENDED")
                .doesNotContain(target.getEmail());
    }

    @Test
    @DisplayName("같은 회원을 동시에 정지해도 모두 성공하고 감사 로그는 한 건이다")
    void concurrentSuspendIsSerialized() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            Callable<Void> suspend =
                    () -> {
                        adminMemberService.suspend(admin.getPublicId(), target.getPublicId());
                        return null;
                    };
            List<Future<Void>> results = executor.invokeAll(List.of(suspend, suspend, suspend));
            for (Future<Void> result : results) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(statusOf(target)).isEqualTo(MemberStatus.SUSPENDED);
        assertThat(auditLog.list).hasSize(1);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            suspendRequest(Member member) {
        return post(MEMBERS + "/{id}/suspend", member.getPublicId());
    }

    private MockHttpSession login(Member member) throws Exception {
        MvcResult result = loginRequest(member).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions loginRequest(Member member) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        java.util.Map.of(
                                                "email",
                                                member.getEmail(),
                                                "password",
                                                PASSWORD))));
    }

    private MemberStatus statusOf(Member member) {
        return members.findStatusById(member.getId()).orElseThrow();
    }

    private JsonNode readBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Member member(String prefix, Role role, MemberStatus memberStatus) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member saved =
                members.save(
                        Member.builder()
                                .email(prefix + "-" + unique + "@snowthing.com")
                                .password(passwordEncoder.encode(PASSWORD))
                                .nickname(prefix + "-" + unique)
                                .role(role)
                                .status(memberStatus)
                                .build());
        memberIds.add(saved.getId());
        return saved;
    }
}
