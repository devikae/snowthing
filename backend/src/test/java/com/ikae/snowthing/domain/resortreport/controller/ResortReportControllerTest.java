package com.ikae.snowthing.domain.resortreport.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportCreateRequest;
import com.ikae.snowthing.domain.resortreport.service.ResortReportService;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(ResortReportControllerTest.FixedClockConfig.class)
class ResortReportControllerTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-12-06T06:30:00Z"), ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ResortRepository resortRepository;
    @Autowired private ResortReportService resortReportService;

    private Member member;
    private CustomUserDetails userDetails;
    private Resort resort;

    @BeforeEach
    void setUp() {
        member =
                memberRepository.save(
                        Member.builder()
                                .email("testrider@snowthing.com")
                                .password("password123!")
                                .nickname("테스트라이더")
                                .build());
        userDetails = new CustomUserDetails(member);

        resort =
                resortRepository.save(
                        Resort.builder()
                                .name("하이원리조트")
                                .code("HIGH1_TEST")
                                .regionName("강원")
                                .displayOrder(1)
                                .active(true)
                                .build());
    }

    @Test
    @DisplayName("로그인 회원이 올바른 설질 제보를 등록하면 201 Created를 반환한다")
    void createReport_Authenticated_Success() throws Exception {
        ResortReportCreateRequest request =
                ResortReportCreateRequest.builder()
                        .resortId(resort.getId())
                        .content("마운틴탑 아테나 슬로프 설질 훌륭합니다.")
                        .build();

        mockMvc.perform(
                        post("/api/v1/resort-reports")
                                .with(csrf())
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").isNumber())
                .andExpect(jsonPath("$.resortName").value("하이원리조트"))
                .andExpect(jsonPath("$.authorNickname").value("테스트라이더"))
                .andExpect(jsonPath("$.content").value("마운틴탑 아테나 슬로프 설질 훌륭합니다."));
    }

    @Test
    @DisplayName("비로그인 사용자가 설질 제보를 등록하려고 하면 401 Unauthorized를 반환한다")
    void createReport_Unauthenticated_Fail() throws Exception {
        ResortReportCreateRequest request =
                ResortReportCreateRequest.builder()
                        .resortId(resort.getId())
                        .content("익명 제보 시도")
                        .build();

        mockMvc.perform(
                        post("/api/v1/resort-reports")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("내용이 비어있는 설질 제보를 등록하면 400 Bad Request를 반환한다")
    void createReport_InvalidContent_Fail() throws Exception {
        ResortReportCreateRequest request =
                ResortReportCreateRequest.builder().resortId(resort.getId()).content("   ").build();

        mockMvc.perform(
                        post("/api/v1/resort-reports")
                                .with(csrf())
                                .with(user(userDetails))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비로그인 사용자도 오늘 등록된 설질 제보 목록을 정상 조회할 수 있다 (200 OK)")
    void getTodayReports_Success() throws Exception {
        // 제보 1건 등록
        resortReportService.createReport(
                member.getId(),
                ResortReportCreateRequest.builder()
                        .resortId(resort.getId())
                        .content("오늘 설질 굿!")
                        .build());

        mockMvc.perform(get("/api/v1/resort-reports/today").param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].content").value("오늘 설질 굿!"))
                .andExpect(jsonPath("$.content[0].resortName").value("하이원리조트"))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(1));
    }

    @Test
    @DisplayName("작성자는 자신의 설질 제보를 삭제할 수 있다")
    void deleteOwnReport() throws Exception {
        long reportId =
                resortReportService
                        .createReport(
                                member.getId(),
                                ResortReportCreateRequest.builder()
                                        .resortId(resort.getId())
                                        .content("삭제할 제보")
                                        .build())
                        .getReportId();

        mockMvc.perform(
                        delete("/api/v1/resort-reports/{reportId}", reportId)
                                .with(csrf())
                                .with(user(userDetails)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETED is returned as INVALID_INPUT when requested by an admin")
    void updateModerationStatus_Deleted_ReturnsBadRequest() throws Exception {
        Member admin =
                memberRepository.save(
                        Member.builder()
                                .email("admin@snowthing.com")
                                .password("password123!")
                                .nickname("admin")
                                .role(Role.ROLE_ADMIN)
                                .build());
        long reportId =
                resortReportService
                        .createReport(
                                member.getId(),
                                ResortReportCreateRequest.builder()
                                        .resortId(resort.getId())
                                        .content("invalid moderation status")
                                        .build())
                        .getReportId();

        mockMvc.perform(
                        patch("/api/v1/admin/resort-reports/{reportId}/moderation-status", reportId)
                                .with(csrf())
                                .with(user(new CustomUserDetails(admin)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"moderationStatus\":\"DELETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_INPUT"));
    }
}
