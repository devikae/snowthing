package com.ikae.snowthing.domain.member.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataControllerTest {

    @Autowired private MockMvc mockMvc;

    @Autowired
    private com.ikae.snowthing.domain.member.repository.ResortRepository resortRepository;

    @Autowired
    private com.ikae.snowthing.domain.member.repository.RidingStyleRepository ridingStyleRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        if (resortRepository.count() == 0) {
            resortRepository.saveAll(
                    java.util.List.of(
                            com.ikae.snowthing.domain.member.entity.Resort.builder()
                                    .name("휘닉스파크")
                                    .regionName("강원 평창")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.Resort.builder()
                                    .name("하이원리조트")
                                    .regionName("강원 정선")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.Resort.builder()
                                    .name("모나용평")
                                    .regionName("강원 평창")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.Resort.builder()
                                    .name("비발디파크")
                                    .regionName("강원 홍천")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.Resort.builder()
                                    .name("웰리힐리파크")
                                    .regionName("강원 횡성")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.Resort.builder()
                                    .name("지산리조트")
                                    .regionName("경기 이천")
                                    .build()));
        }
        if (ridingStyleRepository.count() == 0) {
            ridingStyleRepository.saveAll(
                    java.util.List.of(
                            com.ikae.snowthing.domain.member.entity.RidingStyle.builder()
                                    .styleName("올라운드")
                                    .description("설명")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.RidingStyle.builder()
                                    .styleName("라이딩 / 카빙")
                                    .description("설명")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.RidingStyle.builder()
                                    .styleName("그라운드 트릭")
                                    .description("설명")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.RidingStyle.builder()
                                    .styleName("파크 / 기물 / 파이프")
                                    .description("설명")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.RidingStyle.builder()
                                    .styleName("입문 / 초보")
                                    .description("설명")
                                    .build(),
                            com.ikae.snowthing.domain.member.entity.RidingStyle.builder()
                                    .styleName("관광 / 크루징")
                                    .description("설명")
                                    .build()));
        }
    }

    @Test
    @DisplayName(
            "[마스터 데이터 API 통합 테스트] GET /api/v1/master/resorts 호출 시 6대 스키장 마스터 목록이 비회원에게도 반환되어야 한다")
    void getResorts_Returns6Resorts_PermitAll() throws Exception {
        mockMvc.perform(get("/api/v1/master/resorts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("휘닉스파크"))
                .andExpect(jsonPath("$[1].name").value("하이원리조트"));
    }

    @Test
    @DisplayName(
            "[마스터 데이터 API 통합 테스트] GET /api/v1/master/riding-styles 호출 시 올라운드를 포함한 6대 라이딩 성향 마스터 목록이 반환되어야 한다")
    void getRidingStyles_Returns6Styles_PermitAll() throws Exception {
        mockMvc.perform(get("/api/v1/master/riding-styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].styleName").value("올라운드"));
    }
}
