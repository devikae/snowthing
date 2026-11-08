package com.ikae.snowthing.domain.resortweather.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;

/** 실제 SecurityConfig로 기온 API가 비로그인에 열려 있는지 본다. 소스가 등록되지 않은 리조트를 만들어 외부 호출 없이 204를 확인한다. */
@SpringBootTest(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=2",
            // 클래스가 끝나면 컨텍스트를 닫는다. create-drop이면 닫힐 때 공유 테스트 스키마를 지워 다른 컨텍스트가 깨진다.
            "spring.jpa.hibernate.ddl-auto=update"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ResortWeatherApiAccessTest {

    private static final String RESORT_WITHOUT_SOURCES = "WEATHER_ACCESS_TEST";

    @Autowired private MockMvc mockMvc;
    @Autowired private ResortRepository resortRepository;

    @BeforeEach
    void setUp() {
        if (resortRepository.findByCode(RESORT_WITHOUT_SOURCES).isEmpty()) {
            resortRepository.save(
                    Resort.builder()
                            .name("기온 접근 테스트 리조트")
                            .regionName("테스트")
                            .code(RESORT_WITHOUT_SOURCES)
                            .displayOrder(999)
                            .active(true)
                            .build());
        }
    }

    @AfterEach
    void tearDown() {
        resortRepository.findByCode(RESORT_WITHOUT_SOURCES).ifPresent(resortRepository::delete);
    }

    @Test
    @DisplayName("비로그인으로 호출할 수 있고, 보여 줄 값이 없으면 204")
    void anonymousCallGetsNoContent() throws Exception {
        mockMvc.perform(get("/api/v1/resort-cams/" + RESORT_WITHOUT_SOURCES + "/weather"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("비로그인으로 없는 리조트 코드를 부르면 401이 아니라 404")
    void anonymousCallForUnknownResortIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/resort-cams/NOT_A_RESORT/weather"))
                .andExpect(status().isNotFound());
    }
}
