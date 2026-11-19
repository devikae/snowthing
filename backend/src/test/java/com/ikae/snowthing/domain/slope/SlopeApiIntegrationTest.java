package com.ikae.snowthing.domain.slope;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
import com.ikae.snowthing.domain.member.entity.ResortCountry;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.slope.entity.Slope;
import com.ikae.snowthing.domain.slope.entity.SlopeConnection;
import com.ikae.snowthing.domain.slope.entity.SlopeDifficulty;
import com.ikae.snowthing.domain.slope.entity.SlopeRelation;
import com.ikae.snowthing.domain.slope.repository.SlopeConnectionRepository;
import com.ikae.snowthing.domain.slope.repository.SlopeRepository;

/**
 * 슬로프 제원 공개 API를 실제 DB와 SecurityConfig로 확인한다. 일본 리조트가 커뮤니티 리조트 목록에 섞이지 않는지도 함께 본다. 다른 테스트의 리조트와 겹치지
 * 않게 전용 코드를 쓴다.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=2",
            "spring.jpa.hibernate.ddl-auto=update"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SlopeApiIntegrationTest {

    private static final String KR = "SLOPE_SPEC_KR";
    private static final String JP = "SLOPE_SPEC_JP";
    private static final String KR_OFF = "SLOPE_SPEC_KR_OFF";
    private static final List<String> CODES = List.of(KR, JP, KR_OFF);

    @Autowired private MockMvc mockMvc;
    @Autowired private ResortRepository resortRepository;
    @Autowired private SlopeRepository slopeRepository;
    @Autowired private SlopeConnectionRepository connectionRepository;

    private Slope base;
    private Slope merging;

    @BeforeEach
    void setUp() {
        cleanUp();
        Resort kr = resortRepository.save(resort(KR, "제원테스트국내", ResortCountry.KR, true));
        // 운영과 같이 일본 리조트는 비활성으로 둔다. 슬로프 제원 화면에는 보여야 한다.
        Resort jp = resortRepository.save(resort(JP, "제원테스트일본", ResortCountry.JP, false));
        Resort krOff = resortRepository.save(resort(KR_OFF, "제원테스트국내비활성", ResortCountry.KR, false));

        base = slopeRepository.save(slope(kr, "기준", 10.0, 20.0, 1000, 1));
        merging = slopeRepository.save(slope(kr, "합류", 8.0, null, 500, 2));
        slopeRepository.save(slope(jp, "비슷한 코스", 10.5, null, 900, 1));
        slopeRepository.save(slope(jp, "먼 코스", 20.0, null, 900, 2));
        slopeRepository.save(slope(jp, "최대만 공개", null, 21.0, 900, 3));
        slopeRepository.save(slope(krOff, "비활성 리조트 코스", 10.0, null, 1000, 1));
        connectionRepository.save(
                SlopeConnection.builder()
                        .slope(base)
                        .relatedSlope(merging)
                        .relation(SlopeRelation.MERGE)
                        .build());
        connectionRepository.save(
                SlopeConnection.builder()
                        .slope(merging)
                        .relatedSlope(base)
                        .relation(SlopeRelation.CONTINUE)
                        .build());
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        List<Resort> resorts =
                CODES.stream().flatMap(code -> resortRepository.findByCode(code).stream()).toList();
        if (resorts.isEmpty()) {
            return;
        }
        List<Slope> slopes =
                slopeRepository.findAllByResortIdIn(resorts.stream().map(Resort::getId).toList());
        connectionRepository.deleteAll(
                connectionRepository.findAllBySlopeIdIn(
                        slopes.stream().map(Slope::getId).toList()));
        slopeRepository.deleteAll(slopes);
        resortRepository.deleteAll(resorts);
    }

    private static Resort resort(String code, String name, ResortCountry country, boolean active) {
        return Resort.builder()
                .name(name)
                .regionName("테스트")
                .code(code)
                .displayOrder(900)
                .active(active)
                .country(country)
                .build();
    }

    private static Slope slope(
            Resort resort, String name, Double avg, Double max, Integer length, int order) {
        return Slope.builder()
                .resort(resort)
                .name(name)
                .officialDifficulty("중급")
                .difficulty(SlopeDifficulty.INTERMEDIATE)
                .avgGradient(avg == null ? null : BigDecimal.valueOf(avg))
                .maxGradient(max == null ? null : BigDecimal.valueOf(max))
                .lengthM(length)
                .sourceUrl("https://example.invalid/" + name)
                .checkedAt(LocalDate.of(2026, 11, 20))
                .displayOrder(order)
                .build();
    }

    @Test
    @DisplayName("리조트 목록은 로그인 없이 볼 수 있고, 제원이 있는 국내 활성 리조트와 해외 리조트를 나라와 함께 준다")
    void listsActiveResortsWithSlopes() throws Exception {
        mockMvc.perform(get("/api/v1/slopes/resorts"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(jsonPath("$.resorts[?(@.code == '" + KR + "')].country").value("KR"))
                .andExpect(jsonPath("$.resorts[?(@.code == '" + KR + "')].slopeCount").value(2))
                .andExpect(jsonPath("$.resorts[?(@.code == '" + JP + "')].country").value("JP"))
                .andExpect(jsonPath("$.resorts[*].code", not(hasItem(KR_OFF))));
    }

    @Test
    @DisplayName("리조트 한 곳은 슬로프 순서대로, 리조트 안 관계와 함께 준다")
    void returnsResortWithSlopesAndConnections() throws Exception {
        mockMvc.perform(get("/api/v1/slopes/resorts/" + KR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resort.country").value("KR"))
                .andExpect(jsonPath("$.slopes[0].name").value("기준"))
                .andExpect(jsonPath("$.slopes[1].maxGradient").doesNotExist())
                .andExpect(jsonPath("$.connections", hasSize(2)));
    }

    @Test
    @DisplayName("슬로프 상세는 주변 관계와, 같은 지표로 3도 안에 드는 반대 나라 슬로프만 비슷한 슬로프로 준다")
    void returnsSimilarSlopesFromOtherCountry() throws Exception {
        mockMvc.perform(get("/api/v1/slopes/" + base.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neighbors[0].name").value("합류"))
                .andExpect(jsonPath("$.neighbors[0].relation").value("MERGE"))
                .andExpect(jsonPath("$.similar[*].slope.name", hasItem("비슷한 코스")))
                .andExpect(jsonPath("$.similar[*].slope.name", not(hasItem("먼 코스"))))
                .andExpect(jsonPath("$.similar[*].slope.name", not(hasItem("비활성 리조트 코스"))))
                .andExpect(jsonPath("$.similar[*].slope.name", not(hasItem("합류"))))
                .andExpect(jsonPath("$.similar[?(@.slope.name == '비슷한 코스')].metric").value("AVG"))
                .andExpect(jsonPath("$.similar[?(@.slope.name == '최대만 공개')].metric").value("MAX"));
    }

    @Test
    @DisplayName("없는 리조트, 비활성 국내 리조트, 형식이 틀린 코드는 404, 비교 대상이 2곳 미만이면 400")
    void rejectsUnknownResortsAndBadCompare() throws Exception {
        mockMvc.perform(get("/api/v1/slopes/resorts/" + KR_OFF)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/slopes/resorts/bad-code")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/slopes/999999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/slopes/compare").param("codes", KR))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비교는 고른 순서대로 슬로프 수, 최장 길이, 난이도 분포를 준다")
    void comparesResorts() throws Exception {
        mockMvc.perform(get("/api/v1/slopes/compare").param("codes", KR + "," + JP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resorts[0].resort.code").value(KR))
                .andExpect(jsonPath("$.resorts[0].slopeCount").value(2))
                .andExpect(jsonPath("$.resorts[0].longestLengthM").value(1000))
                .andExpect(jsonPath("$.resorts[0].meanAvgGradient").value(9.0))
                .andExpect(jsonPath("$.resorts[1].difficultyCounts.INTERMEDIATE").value(3));
    }

    @Test
    @DisplayName("커뮤니티 리조트 목록(가입, 프로필, 카풀)에는 일본 리조트가 나오지 않는다")
    void masterResortListExcludesJapaneseResorts() throws Exception {
        mockMvc.perform(get("/api/v1/master/resorts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("제원테스트국내")))
                .andExpect(jsonPath("$[*].name", not(hasItem("제원테스트일본"))));
    }
}
