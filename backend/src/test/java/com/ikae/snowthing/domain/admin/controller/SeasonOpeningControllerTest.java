package com.ikae.snowthing.domain.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ikae.snowthing.domain.admin.dto.SeasonOpeningItem;
import com.ikae.snowthing.domain.admin.dto.SeasonOpeningResponse;
import com.ikae.snowthing.domain.admin.service.SeasonOpeningService;
import com.ikae.snowthing.global.exception.GlobalExceptionHandler;
import com.ikae.snowthing.global.notification.ServerErrorAlerter;

/** 응답 모양, 캐시 헤더, 요청 형식 검증만 본다. 실제 보안 설정의 권한은 SeasonOpeningIntegrationTest에서 본다. */
class SeasonOpeningControllerTest {

    private final SeasonOpeningService service = mock(SeasonOpeningService.class);
    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(
                            new PublicSeasonOpeningController(service),
                            new AdminSeasonOpeningController(service))
                    .setControllerAdvice(
                            new GlobalExceptionHandler(
                                    new StaticListableBeanFactory()
                                            .getBeanProvider(ServerErrorAlerter.class)))
                    .build();

    @Test
    @DisplayName("공개 조회는 스위치와 리조트 목록을 주고 브라우저가 매번 다시 묻게 한다")
    void publicReadWithNoCache() throws Exception {
        when(service.getSeasonOpenings())
                .thenReturn(
                        new SeasonOpeningResponse(
                                true,
                                "개장까지",
                                true,
                                List.of(
                                        new SeasonOpeningItem(
                                                "YONGPYONG",
                                                "모나용평",
                                                LocalDate.of(2026, 11, 22),
                                                true,
                                                true,
                                                1),
                                        new SeasonOpeningItem(
                                                "KONJIAM", "곤지암", null, false, false, 2))));

        mockMvc.perform(get("/api/v1/public/season-openings"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(jsonPath("$.bannerEnabled").value(true))
                .andExpect(jsonPath("$.bannerTitle").value("개장까지"))
                .andExpect(jsonPath("$.showAfterOpening").value(true))
                .andExpect(jsonPath("$.resorts[0].resortCode").value("YONGPYONG"))
                .andExpect(jsonPath("$.resorts[0].name").value("모나용평"))
                .andExpect(jsonPath("$.resorts[0].openingDate").value("2026-11-22"))
                .andExpect(jsonPath("$.resorts[0].confirmed").value(true))
                .andExpect(jsonPath("$.resorts[0].showOnBanner").value(true))
                .andExpect(jsonPath("$.resorts[0].bannerOrder").value(1))
                .andExpect(jsonPath("$.resorts[1].openingDate").doesNotExist());
    }

    @Test
    @DisplayName("저장 요청에서 순서가 0이거나 스위치가 없거나 목록이 비거나 빈 원소가 있으면 서비스를 부르지 않고 400")
    void rejectsMalformedRequest() throws Exception {
        List<String> bodies =
                List.of(
                        "{\"bannerEnabled\":true,\"resorts\":[{\"resortCode\":\"HIGH1\",\"openingDate\":\"2026-11-24\",\"bannerOrder\":0}]}",
                        "{\"resorts\":[{\"resortCode\":\"HIGH1\",\"bannerOrder\":1}]}",
                        "{\"bannerEnabled\":true,\"resorts\":[]}",
                        "{\"bannerEnabled\":true,\"resorts\":[null]}",
                        "{\"bannerEnabled\":true,\"resorts\":[{\"resortCode\":\"\",\"bannerOrder\":1}]}",
                        "{\"bannerEnabled\":true,\"resorts\":[{\"resortCode\":\"HIGH1\",\"openingDate\":\"2026-13-40\",\"bannerOrder\":1}]}");
        for (String body : bodies) {
            mockMvc.perform(
                            put("/api/v1/admin/season-openings")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(service, never()).update(any(), any());
    }
}
