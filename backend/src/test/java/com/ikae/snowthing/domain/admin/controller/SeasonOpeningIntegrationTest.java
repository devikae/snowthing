package com.ikae.snowthing.domain.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.admin.entity.SeasonOpening;
import com.ikae.snowthing.domain.admin.repository.SeasonOpeningRepository;
import com.ikae.snowthing.domain.admin.service.SeasonOpeningService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;

/** 개장일 관리 통합 테스트 (IKA-87). 실제 보안 설정과 로그인 세션으로 공개 조회, 관리자 권한, 저장과 조치 기록을 본다. */
@SpringBootTest
@AutoConfigureMockMvc
class SeasonOpeningIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String PUBLIC = "/api/v1/public/season-openings";
    private static final String ADMIN = "/api/v1/admin/season-openings";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MemberRepository members;
    @Autowired private SeasonOpeningRepository openings;

    private final List<Long> memberIds = new ArrayList<>();
    private Member admin;
    private MockHttpSession adminSession;
    private MockHttpSession userSession;

    @BeforeEach
    void setUp() throws Exception {
        clearOpenings();
        openings.save(opening("YONGPYONG", "모나용평", LocalDate.of(2026, 11, 22), true, 1));
        openings.save(opening("HIGH1", "하이원", LocalDate.of(2026, 11, 24), true, 2));
        admin = member("adm", Role.ROLE_ADMIN);
        adminSession = login(admin);
        userSession = login(member("usr", Role.ROLE_USER));
    }

    @AfterEach
    void tearDown() {
        clearOpenings();
        for (Long memberId : memberIds) {
            jdbc.update("DELETE FROM admin_action_log WHERE actor_member_id = ?", memberId);
            jdbc.update("DELETE FROM member WHERE member_id = ?", memberId);
        }
    }

    @Test
    @DisplayName("공개 조회는 로그인 없이 200이고 배너 순서대로 준다")
    void publicReadWithoutLogin() throws Exception {
        mockMvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerEnabled").value(true))
                .andExpect(jsonPath("$.resorts[0].resortCode").value("YONGPYONG"))
                .andExpect(jsonPath("$.resorts[1].resortCode").value("HIGH1"))
                .andExpect(jsonPath("$.resorts[1].openingDate").value("2026-11-24"));
    }

    @Test
    @DisplayName("관리자 조회와 저장은 일반 회원 403, 비로그인 401이고 값이 바뀌지 않는다")
    void adminApisAreAdminOnly() throws Exception {
        mockMvc.perform(get(ADMIN).session(userSession)).andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(ADMIN).session(adminSession)).andExpect(status().isOk());

        mockMvc.perform(saveRequest(false, "2026-12-01").session(userSession).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(saveRequest(false, "2026-12-01").with(csrf()))
                .andExpect(status().isUnauthorized());

        assertThat(dateOf("HIGH1")).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    @DisplayName("관리자가 저장하면 공개 조회에 바로 보이고 조치 기록이 남는다")
    void adminSaveShowsOnPublicRead() throws Exception {
        mockMvc.perform(saveRequest(false, "2026-12-01").session(adminSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerEnabled").value(false))
                .andExpect(jsonPath("$.resorts[0].resortCode").value("HIGH1"));

        mockMvc.perform(get(PUBLIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerEnabled").value(false))
                .andExpect(jsonPath("$.resorts[0].resortCode").value("HIGH1"))
                .andExpect(jsonPath("$.resorts[0].openingDate").value("2026-12-01"))
                .andExpect(jsonPath("$.resorts[0].showOnBanner").value(false));

        Integer logs =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM admin_action_log WHERE actor_member_id = ?"
                                + " AND action = 'SEASON_OPENING_UPDATE' AND target_type = 'SEASON_OPENING'",
                        Integer.class,
                        admin.getId());
        assertThat(logs).isEqualTo(1);
    }

    @Test
    @DisplayName("배너 순서가 겹치는 저장은 400이고 값이 바뀌지 않는다")
    void duplicatedOrderIsRejected() throws Exception {
        Map<String, Object> body =
                Map.of(
                        "bannerEnabled",
                        true,
                        "resorts",
                        List.of(
                                item("YONGPYONG", "2026-11-22", true, 1),
                                item("HIGH1", "2026-12-01", true, 1)));
        mockMvc.perform(
                        put(ADMIN)
                                .session(adminSession)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ADMIN_004"));

        assertThat(dateOf("HIGH1")).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    /** HIGH1을 배너 순서 1로 올리고 날짜를 바꾸며 배너에서 뺀다. */
    private MockHttpServletRequestBuilder saveRequest(boolean bannerEnabled, String high1Date)
            throws Exception {
        Map<String, Object> body =
                Map.of(
                        "bannerEnabled",
                        bannerEnabled,
                        "resorts",
                        List.of(
                                item("YONGPYONG", "2026-11-22", true, 2),
                                item("HIGH1", high1Date, false, 1)));
        return put(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    private static Map<String, Object> item(
            String code, String date, boolean showOnBanner, int order) {
        return Map.of(
                "resortCode",
                code,
                "openingDate",
                date,
                "confirmed",
                showOnBanner,
                "showOnBanner",
                showOnBanner,
                "bannerOrder",
                order);
    }

    private LocalDate dateOf(String code) {
        return openings.findAll().stream()
                .filter(opening -> opening.getResortCode().equals(code))
                .findFirst()
                .orElseThrow()
                .getOpeningDate();
    }

    private void clearOpenings() {
        openings.deleteAll();
        jdbc.update(
                "DELETE FROM system_config WHERE config_key = ?",
                SeasonOpeningService.KEY_BANNER_ENABLED);
    }

    private static SeasonOpening opening(
            String code, String name, LocalDate date, boolean banner, int order) {
        return SeasonOpening.builder()
                .resortCode(code)
                .resortName(name)
                .openingDate(date)
                .confirmed(banner)
                .showOnBanner(banner)
                .bannerOrder(order)
                .build();
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
