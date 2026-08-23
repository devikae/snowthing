package com.ikae.snowthing.domain.member.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MasterDataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("[마스터 데이터 API 통합 테스트] GET /api/v1/master/resorts 호출 시 6대 스키장 마스터 목록이 비회원에게도 반환되어야 한다")
    void getResorts_Returns6Resorts_PermitAll() throws Exception {
        mockMvc.perform(get("/api/v1/master/resorts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("휘닉스파크"))
                .andExpect(jsonPath("$[1].name").value("하이원리조트"));
    }

    @Test
    @DisplayName("[마스터 데이터 API 통합 테스트] GET /api/v1/master/riding-styles 호출 시 올라운드를 포함한 6대 라이딩 성향 마스터 목록이 반환되어야 한다")
    void getRidingStyles_Returns6Styles_PermitAll() throws Exception {
        mockMvc.perform(get("/api/v1/master/riding-styles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].styleName").value("올라운드"));
    }
}
