package com.ikae.snowthing.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.repository.PostCategoryRepository;

/**
 * 게시판 관리 통합 테스트 (IKA-114). 실제 보안 설정과 로그인 세션으로 공개 목록, 관리자 권한, 추가와 수정과 닫기와 삭제와 복구와 순서, 조치 기록을 본다. 다른
 * 테스트와 DB를 같이 쓰므로 이 테스트가 만든 게시판만 건드리고 끝나면 지운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BoardAdminIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String PUBLIC = "/api/v1/public/boards";
    private static final String ADMIN = "/api/v1/admin/boards";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberRepository members;
    @Autowired private PostCategoryRepository categories;

    private final List<Long> memberIds = new ArrayList<>();
    private final List<String> createdCodes = new ArrayList<>();
    private MockHttpSession adminSession;
    private MockHttpSession userSession;

    @BeforeEach
    void setUp() throws Exception {
        adminSession = login(member("badm", Role.ROLE_ADMIN));
        userSession = login(member("busr", Role.ROLE_USER));
    }

    @AfterEach
    void tearDown() {
        for (String code : createdCodes) {
            jdbc.update("DELETE FROM post_category WHERE code = ?", code);
        }
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM admin_action_log WHERE actor_member_id = ?", memberId);
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("관리자 API는 일반 회원 403, 비로그인 401이고 게시판이 생기지 않는다")
    void adminApisAreAdminOnly() throws Exception {
        String code = newCode();
        mockMvc.perform(get(ADMIN).session(userSession)).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mockMvc.perform(createRequest(code, "MEMBER", "OPTIONAL").session(userSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(createRequest(code, "MEMBER", "OPTIONAL"))
                .andExpect(status().isUnauthorized());

        assertThat(categories.findByCode(code)).isEmpty();
    }

    @Test
    @DisplayName("추가한 게시판은 공개 목록 맨 뒤에 나오고, 닫거나 삭제하면 빠지고, 복구하면 닫힌 채 돌아온다")
    void createCloseDeleteRestore() throws Exception {
        String code = newCode();
        mockMvc.perform(createRequest(code, "MEMBER", "FORBIDDEN").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.status").value("OPEN"));
        assertThat(publicCodes()).last().isEqualTo(code);
        assertThat(publicCodes()).doesNotContain("MARKET", "CARPOOL", "INQUIRY");

        mockMvc.perform(
                        patch(ADMIN + "/" + code + "/status")
                                .param("status", "HIDDEN")
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"));
        assertThat(publicCodes()).doesNotContain(code);

        mockMvc.perform(
                        patch(ADMIN + "/" + code + "/status")
                                .param("status", "OPEN")
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(delete(ADMIN + "/" + code).session(adminSession).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(publicCodes()).doesNotContain(code);
        assertThat(categories.findByCode(code))
                .get()
                .extracting(PostCategory::isDeleted)
                .isEqualTo(true);

        mockMvc.perform(post(ADMIN + "/" + code + "/restore").session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.deletedAt").doesNotExist());
        assertThat(publicCodes()).doesNotContain(code);

        assertThat(actionsFor(code))
                .contains("BOARD_CREATE", "BOARD_STATUS", "BOARD_DELETE", "BOARD_RESTORE");
    }

    @Test
    @DisplayName("수정은 이름과 규칙을 바꾸고 코드는 바꾸지 않는다")
    void updateKeepsCode() throws Exception {
        String code = newCode();
        mockMvc.perform(createRequest(code, "MEMBER", "OPTIONAL").session(adminSession))
                .andExpect(status().isOk());

        Map<String, Object> body = boardBody("BOGUS_CODE", "MEMBER", "FORBIDDEN");
        body.put("name", "고친 이름");
        mockMvc.perform(
                        patch(ADMIN + "/" + code)
                                .session(adminSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.name").value("고친 이름"))
                .andExpect(jsonPath("$.anonymousMode").value("FORBIDDEN"));
        assertThat(categories.findByCode("BOGUS_CODE")).isEmpty();
    }

    @Test
    @DisplayName("잘못된 코드, 예약 코드, 중복 코드, 누구나인데 익명 선택이면 거절한다")
    void rejectsInvalidBoards() throws Exception {
        mockMvc.perform(createRequest("lower", "MEMBER", "OPTIONAL").session(adminSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_014"));
        mockMvc.perform(createRequest("1ABC", "MEMBER", "OPTIONAL").session(adminSession))
                .andExpect(status().isBadRequest());
        for (String reserved : List.of("MARKET", "INQUIRY", "BEST")) {
            mockMvc.perform(createRequest(reserved, "MEMBER", "OPTIONAL").session(adminSession))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ADMIN_015"));
        }
        String taken = newCode();
        mockMvc.perform(createRequest(taken, "MEMBER", "OPTIONAL").session(adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(createRequest(taken, "MEMBER", "OPTIONAL").session(adminSession))
                .andExpect(status().isConflict());
        String code = newCode();
        mockMvc.perform(createRequest(code, "ALL", "OPTIONAL").session(adminSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_016"));
        assertThat(categories.findByCode(code)).isEmpty();
    }

    @Test
    @DisplayName("전용 화면 카테고리(장터, 카풀, 문의)는 관리자 목록에 없고 바꿀 수 없다")
    void ownScreenCategoriesAreNotManaged() throws Exception {
        MvcResult result =
                mockMvc.perform(get(ADMIN).session(adminSession))
                        .andExpect(status().isOk())
                        .andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("\"MARKET\"", "\"CARPOOL\"", "\"INQUIRY\"");
        mockMvc.perform(delete(ADMIN + "/MARKET").session(adminSession).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMIN_017"));
    }

    @Test
    @DisplayName("위로 올리면 바로 위 게시판과 순서를 맞바꾼다")
    void moveSwapsWithNeighbor() throws Exception {
        String first = newCode();
        String second = newCode();
        mockMvc.perform(createRequest(first, "MEMBER", "OPTIONAL").session(adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(createRequest(second, "MEMBER", "OPTIONAL").session(adminSession))
                .andExpect(status().isOk());
        int firstOrder = categories.findByCode(first).orElseThrow().getSortOrder();
        int secondOrder = categories.findByCode(second).orElseThrow().getSortOrder();

        mockMvc.perform(
                        post(ADMIN + "/" + second + "/move")
                                .param("direction", "UP")
                                .session(adminSession)
                                .with(csrf()))
                .andExpect(status().isOk());

        assertThat(categories.findByCode(second).orElseThrow().getSortOrder())
                .isEqualTo(firstOrder);
        assertThat(categories.findByCode(first).orElseThrow().getSortOrder())
                .isEqualTo(secondOrder);
        List<String> codes = publicCodes();
        assertThat(codes.indexOf(second)).isLessThan(codes.indexOf(first));
    }

    private List<String> publicCodes() throws Exception {
        MvcResult result = mockMvc.perform(get(PUBLIC)).andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> boards =
                objectMapper.readValue(
                        result.getResponse().getContentAsString(),
                        objectMapper
                                .getTypeFactory()
                                .constructCollectionType(List.class, Map.class));
        return boards.stream().map(board -> (String) board.get("code")).toList();
    }

    private List<String> actionsFor(String code) {
        return jdbc.queryForList(
                "SELECT action FROM admin_action_log WHERE target_type = 'BOARD' AND target_id = ?",
                String.class,
                code);
    }

    private String newCode() {
        String code =
                "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        createdCodes.add(code);
        return code;
    }

    private MockHttpServletRequestBuilder createRequest(
            String code, String writePermission, String anonymousMode) throws Exception {
        return post(ADMIN)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        objectMapper.writeValueAsString(
                                boardBody(code, writePermission, anonymousMode)));
    }

    private static Map<String, Object> boardBody(
            String code, String writePermission, String anonymousMode) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "테스트 게시판");
        body.put("shortName", "테스트");
        body.put("description", "테스트 설명");
        body.put("showOnHome", false);
        body.put("writePermission", writePermission);
        body.put("anonymousMode", anonymousMode);
        return body;
    }

    private MockHttpSession login(Member member) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of(
                                                                "email",
                                                                member.getEmail(),
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
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
        memberIds.add(saved.getId());
        return saved;
    }
}
