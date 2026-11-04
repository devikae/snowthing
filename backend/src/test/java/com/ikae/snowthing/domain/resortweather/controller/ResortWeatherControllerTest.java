package com.ikae.snowthing.domain.resortweather.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ikae.snowthing.domain.resortweather.dto.ResortWeatherResponse;
import com.ikae.snowthing.domain.resortweather.service.ResortWeatherService;
import com.ikae.snowthing.domain.resortweather.source.WeatherSourceType;
import com.ikae.snowthing.global.exception.GlobalExceptionHandler;
import com.ikae.snowthing.global.notification.ServerErrorAlerter;

/** 응답 형태와 캐시 헤더만 본다. 실제 보안 설정으로 비로그인 접근은 ResortWeatherApiAccessTest에서 본다. */
class ResortWeatherControllerTest {

    private final ResortWeatherService resortWeatherService = mock(ResortWeatherService.class);
    private final MockMvc mockMvc =
            MockMvcBuilders.standaloneSetup(new ResortWeatherController(resortWeatherService))
                    .setControllerAdvice(
                            new GlobalExceptionHandler(
                                    new StaticListableBeanFactory()
                                            .getBeanProvider(ServerErrorAlerter.class)))
                    .build();

    @Test
    @DisplayName("기온이 있으면 200과 캐시 헤더를 준다")
    void returnsWeatherWithCacheHeader() throws Exception {
        when(resortWeatherService.getWeather("HIGH1"))
                .thenReturn(
                        Optional.of(
                                new ResortWeatherResponse(
                                        "HIGH1",
                                        new BigDecimal("-3.2"),
                                        "맑음",
                                        OffsetDateTime.parse("2026-11-04T17:00:00+09:00"),
                                        WeatherSourceType.OFFICIAL,
                                        false)));

        mockMvc.perform(get("/api/v1/resort-cams/HIGH1/weather"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Cache-Control",
                                        "max-age=120, public, stale-while-revalidate=300"))
                .andExpect(jsonPath("$.temperature").value(-3.2))
                .andExpect(jsonPath("$.sky").value("맑음"))
                .andExpect(jsonPath("$.source").value("OFFICIAL"))
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    @DisplayName("형식이 맞지 않는 코드는 서비스를 부르지 않고 404")
    void rejectsMalformedCode() throws Exception {
        mockMvc.perform(get("/api/v1/resort-cams/high1/weather")).andExpect(status().isNotFound());
    }
}
