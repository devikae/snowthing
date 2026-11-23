package com.ikae.snowthing.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.chat.dto.ChatMessageResponse;
import com.ikae.snowthing.domain.chat.dto.SenderDto;
import com.ikae.snowthing.domain.chat.service.ChatRecentHistoryBuffer;
import com.ikae.snowthing.domain.chat.service.ChatService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.AdminMemberService;

/** 라이브톡 운영 도구와 회원 정지 기간 통합 테스트 (IKA-115). 실제 로그인 세션으로 권한, 조치 기록, 로그인 안내를 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class LiveChatOpsIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberRepository members;
    @Autowired private ChatRecentHistoryBuffer historyBuffer;
    @Autowired private ChatService chatService;
    @Autowired private AdminMemberService adminMemberService;

    private final List<Member> created = new ArrayList<>();
    private final List<String> messageIds = new ArrayList<>();
    private Member admin;
    private Member user;
    private MockHttpSession adminSession;
    private MockHttpSession userSession;

    @BeforeEach
    void setUp() throws Exception {
        admin = member("adm", Role.ROLE_ADMIN);
        user = member("usr", Role.ROLE_USER);
        adminSession = login(admin);
        userSession = login(user);
    }

    @AfterEach
    void tearDown() {
        for (String messageId : messageIds) {
            historyBuffer.removeByMessageId(messageId);
            jdbc.update("DELETE FROM admin_action_log WHERE target_id = ?", messageId);
        }
        for (Member member : created) {
            jdbc.update(
                    "DELETE FROM admin_action_log WHERE actor_member_id = ? OR target_id = ?",
                    member.getId(),
                    member.getPublicId());
            jdbc.update("DELETE FROM member WHERE member_id = ?", member.getId());
        }
    }

    @Test
    @DisplayName("관리자가 가린 메시지는 최근 대화에서 빠지고 조치 기록에 닉네임과 함께 남는다")
    void adminHidesOneMessage() throws Exception {
        ChatMessageResponse kept = appendMessage("남길 메시지");
        ChatMessageResponse hidden = appendMessage("가릴 메시지");

        mockMvc.perform(hide(hidden.messageId()).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(historyBuffer.getRecentMessages())
                .extracting(ChatMessageResponse::messageId)
                .contains(kept.messageId())
                .doesNotContain(hidden.messageId());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT reason FROM admin_action_log"
                                        + " WHERE action = 'CHAT_MESSAGE_HIDE' AND target_type = 'CHAT_MESSAGE'"
                                        + " AND target_id = ? AND actor_member_id = ?",
                                String.class,
                                hidden.messageId(),
                                admin.getId()))
                .isEqualTo("보낸사람");
    }

    @Test
    @DisplayName("최근 대화에서 밀려난 메시지도 가릴 수 있고 기록이 남는다")
    void hidesMessageMissingFromBuffer() throws Exception {
        String messageId = UUID.randomUUID().toString();
        messageIds.add(messageId);

        mockMvc.perform(hide(messageId).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM admin_action_log WHERE target_id = ?",
                                Integer.class,
                                messageId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("가리기는 관리자만 쓸 수 있다. 일반 회원 403, 비로그인 401")
    void hideIsAdminOnly() throws Exception {
        ChatMessageResponse message = appendMessage("남아야 하는 메시지");

        mockMvc.perform(hide(message.messageId()).session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(hide(message.messageId()).with(csrf()))
                .andExpect(status().isUnauthorized());

        assertThat(historyBuffer.getRecentMessages())
                .extracting(ChatMessageResponse::messageId)
                .contains(message.messageId());
    }

    @Test
    @DisplayName("비회원은 보내기 전에 자기 닉네임을 받고, 회원은 받지 않는다")
    void guestNicknamePreview() throws Exception {
        String ip = "198.51.100.20";
        mockMvc.perform(
                        get("/api/v1/chat/me")
                                .with(
                                        request -> {
                                            request.setRemoteAddr(ip);
                                            return request;
                                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestNickname").value(chatService.guestNickname(ip)));

        mockMvc.perform(get("/api/v1/chat/me").session(userSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestNickname").doesNotExist());
    }

    @Test
    @DisplayName("공개 설정에 라이브톡 열림 여부가 들어 있다")
    void publicConfigsExposeChatEnabled() throws Exception {
        mockMvc.perform(get("/api/v1/public/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatEnabled").isBoolean());
    }

    @Test
    @DisplayName("기간 정지는 끝나는 시각과 사유를 저장하고, 비밀번호가 맞으면 로그인 화면에 풀리는 시각을 알려 준다")
    void periodSuspensionShowsEndTimeAfterPassword() throws Exception {
        LocalDateTime before = LocalDateTime.now();

        JsonNode body =
                json(
                        mockMvc.perform(
                                        suspend(user)
                                                .session(adminSession)
                                                .with(csrf())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"days\":7,\"reason\":\"라이브톡 욕설\"}"))
                                .andExpect(status().isOk()));

        assertThat(body.get("status").asText()).isEqualTo("SUSPENDED");
        assertThat(body.get("suspendReason").asText()).isEqualTo("라이브톡 욕설");
        LocalDateTime until = LocalDateTime.parse(body.get("suspendedUntil").asText());
        assertThat(until)
                .isAfterOrEqualTo(before.plusDays(7))
                .isBefore(before.plusDays(7).plusMinutes(1));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT reason FROM admin_action_log"
                                        + " WHERE action = 'MEMBER_SUSPEND' AND target_id = ?",
                                String.class,
                                user.getPublicId()))
                .isEqualTo("7일: 라이브톡 욕설");

        loginRequest(user.getEmail(), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCOUNT_SUSPENDED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("에 풀립니다.")));
        // 비밀번호가 틀리면 지금처럼 없는 계정과 같은 답을 받는다.
        loginRequest(user.getEmail(), "Wrong-password1!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("본문 없이 정지하면 지금처럼 무기한이고 로그인 안내에 시각이 없다")
    void suspendWithoutBodyIsIndefinite() throws Exception {
        JsonNode body =
                json(
                        mockMvc.perform(suspend(user).session(adminSession).with(csrf()))
                                .andExpect(status().isOk()));

        assertThat(body.get("suspendedUntil").isNull()).isTrue();
        loginRequest(user.getEmail(), PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("이용이 정지된 계정입니다."));
    }

    @Test
    @DisplayName("정지 일수는 1일부터 365일까지만 받는다")
    void rejectsOutOfRangeDays() throws Exception {
        for (String days : List.of("0", "366")) {
            mockMvc.perform(
                            suspend(user)
                                    .session(adminSession)
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"days\":" + days + "}"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(members.findById(user.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("기간이 끝난 정지는 자동으로 풀리고 다시 로그인할 수 있다. 무기한 정지는 그대로다")
    void expiredSuspensionIsReleased() throws Exception {
        Member indefinite = member("ind", Role.ROLE_USER);
        adminMemberService.suspend(admin.getPublicId(), user.getPublicId(), 1, "테스트");
        adminMemberService.suspend(admin.getPublicId(), indefinite.getPublicId(), null, null);
        jdbc.update(
                "UPDATE member SET suspended_until = ? WHERE member_id = ?",
                LocalDateTime.now().minusMinutes(1),
                user.getId());

        assertThat(adminMemberService.releaseExpiredSuspensions(100)).isGreaterThanOrEqualTo(1);

        Member released = members.findById(user.getId()).orElseThrow();
        assertThat(released.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(released.getSuspendedUntil()).isNull();
        assertThat(released.getSuspendReason()).isNull();
        assertThat(members.findById(indefinite.getId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.SUSPENDED);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT reason FROM admin_action_log"
                                        + " WHERE action = 'MEMBER_REINSTATE' AND target_id = ?",
                                String.class,
                                user.getPublicId()))
                .isEqualTo("정지 기간 만료");
        loginRequest(user.getEmail(), PASSWORD).andExpect(status().isOk());
    }

    private ChatMessageResponse appendMessage(String content) {
        ChatMessageResponse message =
                new ChatMessageResponse(
                        UUID.randomUUID().toString(),
                        new SenderDto("anon-" + UUID.randomUUID(), "보낸사람"),
                        null,
                        content,
                        LocalDateTime.now().toString());
        historyBuffer.append(message, "203.0.113.55");
        messageIds.add(message.messageId());
        return message;
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder hide(
            String messageId) {
        return delete("/api/v1/admin/chat/messages/" + messageId);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            suspend(Member target) {
        return post("/api/v1/admin/members/" + target.getPublicId() + "/suspend");
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private ResultActions loginRequest(String email, String password) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/login")
                        .with(csrf())
                        .with(
                                request -> {
                                    // 로그인 시도 제한에 걸리지 않게 테스트마다 다른 IP로 보낸다.
                                    request.setRemoteAddr(
                                            "198.18.0." + (Math.abs(email.hashCode()) % 250 + 1));
                                    return request;
                                })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        Map.of("email", email, "password", password))));
    }

    private MockHttpSession login(Member member) throws Exception {
        MvcResult result =
                loginRequest(member.getEmail(), PASSWORD).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Member member(String prefix, Role role) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Member saved =
                members.save(
                        Member.builder()
                                .email(prefix + "-" + unique + "@snowthing.com")
                                .password(passwordEncoder.encode(PASSWORD))
                                .nickname(prefix + unique)
                                .role(role)
                                .status(MemberStatus.ACTIVE)
                                .build());
        created.add(saved);
        return saved;
    }
}
