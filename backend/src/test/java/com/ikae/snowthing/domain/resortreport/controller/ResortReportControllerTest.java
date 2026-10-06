package com.ikae.snowthing.domain.resortreport.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportCreateRequest;
import com.ikae.snowthing.domain.resortreport.service.ResortReportService;
import com.ikae.snowthing.global.security.CustomUserDetails;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResortReportControllerTest {

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

        mockMvc.perform(get("/api/v1/resort-reports/today").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].content").value("오늘 설질 굿!"))
                .andExpect(jsonPath("$[0].resortName").value("하이원리조트"));
    }
}
