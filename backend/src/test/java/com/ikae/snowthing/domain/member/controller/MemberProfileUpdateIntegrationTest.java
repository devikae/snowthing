package com.ikae.snowthing.domain.member.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
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
import com.ikae.snowthing.domain.member.dto.MemberProfileUpdateRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.RidingStyle;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberResortRepository;
import com.ikae.snowthing.domain.member.repository.MemberRidingStyleRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.member.repository.RidingStyleRepository;
import com.ikae.snowthing.domain.member.service.MemberService;

@SpringBootTest
@AutoConfigureMockMvc
class MemberProfileUpdateIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberResortRepository memberResortRepository;
    @Autowired private MemberRidingStyleRepository memberRidingStyleRepository;
    @Autowired private ResortRepository resortRepository;
    @Autowired private RidingStyleRepository ridingStyleRepository;

    private Long resortId1;
    private Long resortId2;
    private Long styleId1;
    private Long styleId2;

    @BeforeEach
    void setUp() {
        cleanUp();

        Resort r1 =
                resortRepository
                        .findByName("휘닉스파크")
                        .orElseGet(
                                () ->
                                        resortRepository.save(
                                                Resort.builder()
                                                        .name("휘닉스파크")
                                                        .regionName("강원 평창")
                                                        .build()));
        Resort r2 =
                resortRepository
                        .findByName("하이원리조트")
                        .orElseGet(
                                () ->
                                        resortRepository.save(
                                                Resort.builder()
                                                        .name("하이원리조트")
                                                        .regionName("강원 정선")
                                                        .build()));
        resortId1 = r1.getId();
        resortId2 = r2.getId();

        RidingStyle s1 =
                ridingStyleRepository
                        .findByStyleName("올라운드")
                        .orElseGet(
                                () ->
                                        ridingStyleRepository.save(
                                                RidingStyle.builder()
                                                        .styleName("올라운드")
                                                        .description("올라운드")
                                                        .build()));
        RidingStyle s2 =
                ridingStyleRepository
                        .findByStyleName("그라운드 트릭")
                        .orElseGet(
                                () ->
                                        ridingStyleRepository.save(
                                                RidingStyle.builder()
                                                        .styleName("그라운드 트릭")
                                                        .description("그라운드 트릭")
                                                        .build()));
        styleId1 = s1.getId();
        styleId2 = s2.getId();

        MemberSignUpRequest signUpRequest =
                MemberSignUpRequest.builder()
                        .email("profileupdate@snowthing.com")
                        .password("Password123!")
                        .nickname("수정전닉네임")
                        .bio("수정전소개")
                        .departureRegion("서울")
                        .resortIds(List.of(resortId1))
                        .ridingStyleIds(List.of(styleId1))
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
            "[프로필 수정 통합 테스트] 로그인한 유저가 PUT /api/members/me 로 닉네임과 N:M 스키장/성향을 변경 시 DB 중계 데이터가 갱신되고 조회가 반영되어야 한다")
    void updateMyProfile_Success_UpdatesProfileAndMiddleTables() throws Exception {
        MockHttpSession beforeSession = new MockHttpSession();

        MemberLoginRequest loginRequest =
                MemberLoginRequest.builder()
                        .email("profileupdate@snowthing.com")
                        .password("Password123!")
                        .build();

        MvcResult loginResult =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .with(csrf())
                                        .session(beforeSession)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(loginRequest)))
                        .andExpect(status().isOk())
                        .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        MemberProfileUpdateRequest updateRequest =
                MemberProfileUpdateRequest.builder()
                        .nickname("수정후닉네임")
                        .bio("수정후소개입니다")
                        .departureRegion("경기 이천")
                        .resortIds(List.of(resortId1, resortId2))
                        .ridingStyleIds(List.of(styleId1, styleId2))
                        .build();

        mockMvc.perform(
                        put("/api/v1/members/me")
                                .with(csrf())
                                .session(session)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("수정후닉네임"))
                .andExpect(jsonPath("$.resortNames.length()").value(2))
                .andExpect(jsonPath("$.ridingStyleNames.length()").value(2));

        mockMvc.perform(get("/api/v1/members/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("수정후닉네임"))
                .andExpect(jsonPath("$.resortNames", Matchers.hasItems("휘닉스파크", "하이원리조트")));
    }
}
