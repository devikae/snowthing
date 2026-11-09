package com.ikae.snowthing.domain.slopestatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.slopestatus.entity.CollectResult;
import com.ikae.snowthing.domain.slopestatus.entity.ResortSlopeStatus;
import com.ikae.snowthing.domain.slopestatus.repository.ResortSlopeStatusRepository;
import com.ikae.snowthing.domain.slopestatus.service.SlopeStatusCollector;
import com.ikae.snowthing.domain.slopestatus.source.SlopeFetchException;
import com.ikae.snowthing.domain.slopestatus.source.SlopeStatusHttpClient;
import com.ikae.snowthing.domain.slopestatus.source.SlopeStatusParsers;
import com.ikae.snowthing.domain.slopestatus.source.SlopeStatusSourceRegistry;
import com.ikae.snowthing.domain.slopestatus.source.SlopeStatusSourceRegistry.SlopeSource;

/**
 * 수집기, 저장, 공개 API를 실제 DB와 SecurityConfig로 확인한다. 외부 호출은 HTTP 클라이언트를 바꿔 끼워 막는다. 출처 목록도 바꿔 끼워 테스트 전용
 * 리조트 코드를 쓴다(다른 테스트가 만드는 실제 리조트 코드와 겹치지 않게).
 */
@SpringBootTest(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=2",
            "spring.jpa.hibernate.ddl-auto=update",
            "snowthing.slope-status.enabled-resorts=SLOPE_TEST_A,SLOPE_TEST_INACTIVE,SLOPE_TEST_NOSOURCE"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SlopeStatusIntegrationTest {

    private static final String ACTIVE = "SLOPE_TEST_A";
    private static final String DISABLED = "SLOPE_TEST_DISABLED";
    private static final String INACTIVE = "SLOPE_TEST_INACTIVE";
    private static final String NO_SOURCE = "SLOPE_TEST_NOSOURCE";
    private static final List<String> CODES = List.of(ACTIVE, DISABLED, INACTIVE, NO_SOURCE);

    private static final URI FETCH_URI = URI.create("https://example.invalid/slopes");
    private static final String PAGE_URL = "https://example.invalid/slope-page";

    private static final String PAGE_ONE_OPEN =
            """
            <table class="chart-a1"><caption>슬로프 오픈 현황</caption><tbody>
            <tr><td>그램</td><td>초급</td><td class="open">Open</td><td class="close">Close</td><td>Close</td></tr>
            <tr><td>익시오</td><td>초/중급</td><td>Close</td><td>Close</td><td>Close</td></tr>
            </tbody></table>
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private ResortRepository resortRepository;
    @Autowired private ResortSlopeStatusRepository statusRepository;
    @Autowired private SlopeStatusCollector collector;

    @MockitoBean private SlopeStatusHttpClient httpClient;
    @MockitoBean private SlopeStatusSourceRegistry registry;

    @BeforeEach
    void setUp() {
        cleanUp();
        int order = 990;
        for (String code : CODES) {
            resortRepository.save(
                    Resort.builder()
                            .name("슬로프 현황 테스트 " + code)
                            .regionName("테스트")
                            .code(code)
                            .displayOrder(order++)
                            .active(!code.equals(INACTIVE))
                            .build());
        }
        SlopeSource source = new SlopeSource(FETCH_URI, PAGE_URL, SlopeStatusParsers::konjiam);
        given(registry.sourceFor(any())).willReturn(Optional.empty());
        given(registry.sourceFor(ACTIVE)).willReturn(Optional.of(source));
        given(registry.sourceFor(DISABLED)).willReturn(Optional.of(source));
        given(registry.sourceFor(INACTIVE)).willReturn(Optional.of(source));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        for (String code : CODES) {
            resortRepository
                    .findByCode(code)
                    .ifPresent(
                            resort -> {
                                statusRepository
                                        .findByResortId(resort.getId())
                                        .ifPresent(statusRepository::delete);
                                resortRepository.delete(resort);
                            });
        }
    }

    @Test
    @DisplayName("켜 둔 활성 리조트만 부르고, 성공하면 슬로프 목록과 열린 수를 저장해 공개 API로 보여 준다")
    void collectAndShow() throws Exception {
        given(httpClient.get(FETCH_URI)).willReturn(bytes(PAGE_ONE_OPEN));

        int attempted = collector.collectEnabled();

        assertThat(attempted).isEqualTo(1);
        verify(httpClient).get(eq(FETCH_URI));
        ResortSlopeStatus saved = statusOf(ACTIVE);
        assertThat(saved.getLastResult()).isEqualTo(CollectResult.SUCCESS);
        assertThat(saved.getOpenSlopeCount()).isEqualTo(1);
        assertThat(saved.getTotalSlopeCount()).isEqualTo(2);
        assertThat(saved.getCollectedAt()).isNotNull();
        assertThat(saved.getSourceUrl()).isEqualTo(PAGE_URL);

        String item = "$.resorts[?(@.resortCode == '" + ACTIVE + "')]";
        mockMvc.perform(get("/api/v1/slope-status"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("max-age=300")))
                .andExpect(header().string("Cache-Control", containsString("public")))
                .andExpect(jsonPath(item + ".supported", contains(true)))
                .andExpect(jsonPath(item + ".openSlopeCount", contains(1)))
                .andExpect(jsonPath(item + ".totalSlopeCount", contains(2)))
                .andExpect(jsonPath(item + ".stale", contains(false)))
                .andExpect(jsonPath(item + ".sourceUrl", contains(PAGE_URL)))
                .andExpect(jsonPath(item + ".slopes[0].name", contains("그램")))
                .andExpect(jsonPath(item + ".slopes[0].open", contains(true)))
                .andExpect(jsonPath(item + ".slopes[0].day", contains("OPEN")))
                .andExpect(jsonPath(item + ".slopes[1].open", contains(false)))
                .andExpect(jsonPath(item + ".collectedAt", contains(containsString("+09:00"))));
    }

    @Test
    @DisplayName("성공 뒤 실패하면 마지막 성공 값과 시각을 그대로 두고 stale로 보여 준다. 실패 사유는 응답에 넣지 않는다")
    void failureKeepsLastSuccess() throws Exception {
        given(httpClient.get(FETCH_URI)).willReturn(bytes(PAGE_ONE_OPEN));
        collector.collectEnabled();
        ResortSlopeStatus first = statusOf(ACTIVE);
        LocalDateTime firstCollectedAt = first.getCollectedAt();
        String firstJson = first.getSlopesJson();

        given(httpClient.get(FETCH_URI)).willThrow(new SlopeFetchException("외부 호출 실패: Timeout"));
        collector.collectEnabled();

        ResortSlopeStatus after = statusOf(ACTIVE);
        assertThat(after.getLastResult()).isEqualTo(CollectResult.FAILED);
        assertThat(after.getFailureReason()).isEqualTo("외부 호출 실패: Timeout");
        assertThat(after.getCollectedAt()).isEqualTo(firstCollectedAt);
        assertThat(after.getSlopesJson()).isEqualTo(firstJson);
        assertThat(after.getOpenSlopeCount()).isEqualTo(1);
        assertThat(after.getLastAttemptAt()).isAfterOrEqualTo(firstCollectedAt);

        String item = "$.resorts[?(@.resortCode == '" + ACTIVE + "')]";
        mockMvc.perform(get("/api/v1/slope-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item + ".stale", contains(true)))
                .andExpect(jsonPath(item + ".openSlopeCount", contains(1)))
                .andExpect(jsonPath(item + ".slopes[*]", hasSize(2)))
                .andExpect(content().string(not(containsString("Timeout"))))
                .andExpect(content().string(not(containsString("failureReason"))));
    }

    @Test
    @DisplayName("구조가 바뀌어 파싱이 실패해도 실패로 기록한다")
    void parseFailureIsRecorded() {
        given(httpClient.get(FETCH_URI)).willReturn(bytes("<html><body>점검 중</body></html>"));

        collector.collectEnabled();

        ResortSlopeStatus saved = statusOf(ACTIVE);
        assertThat(saved.getLastResult()).isEqualTo(CollectResult.FAILED);
        assertThat(saved.getCollectedAt()).isNull();
        assertThat(saved.getSlopesJson()).isNull();
        assertThat(saved.getFailureReason()).contains("곤지암");
    }

    @Test
    @DisplayName("한 번도 성공하지 못한 리조트는 수집 시각 없이 빈 목록, stale은 false(화면은 정보 준비 중)")
    void firstFailureShowsPreparing() throws Exception {
        given(httpClient.get(FETCH_URI)).willThrow(new SlopeFetchException("응답 본문이 비어 있습니다."));
        collector.collectEnabled();

        String item = "$.resorts[?(@.resortCode == '" + ACTIVE + "')]";
        mockMvc.perform(get("/api/v1/slope-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(item + ".supported", contains(true)))
                .andExpect(jsonPath(item + ".stale", contains(false)))
                .andExpect(jsonPath(item + ".slopes[*]", hasSize(0)))
                .andExpect(jsonPath(item + ".sourceUrl", contains(PAGE_URL)));
    }

    @Test
    @DisplayName("출처 없는 리조트는 supported=false, 비활성 리조트는 목록과 수집에서 빠진다")
    void unsupportedAndInactive() throws Exception {
        given(httpClient.get(FETCH_URI)).willReturn(bytes(PAGE_ONE_OPEN));
        collector.collectEnabled();

        assertThat(resortRepository.findByCode(INACTIVE))
                .get()
                .satisfies(
                        resort ->
                                assertThat(statusRepository.findByResortId(resort.getId()))
                                        .isEmpty());
        assertThat(resortRepository.findByCode(DISABLED))
                .get()
                .satisfies(
                        resort ->
                                assertThat(statusRepository.findByResortId(resort.getId()))
                                        .isEmpty());

        mockMvc.perform(get("/api/v1/slope-status"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.resorts[?(@.resortCode == '" + NO_SOURCE + "')].supported",
                                contains(false)))
                .andExpect(jsonPath("$.resorts[?(@.resortCode == '" + INACTIVE + "')]", hasSize(0)))
                .andExpect(
                        jsonPath(
                                "$.resorts[?(@.resortCode == '" + DISABLED + "')].collectedAt",
                                contains((Object) null)));
    }

    @Test
    @DisplayName("켜 둔 리조트가 없으면 외부 호출을 하지 않는다")
    void nothingEnabledNoCalls() {
        // 켜 둔 목록의 리조트를 모두 지우면 시도할 곳이 없다.
        cleanUp();

        int attempted = collector.collectEnabled();

        assertThat(attempted).isZero();
        verify(httpClient, never()).get(any());
    }

    private ResortSlopeStatus statusOf(String code) {
        Resort resort = resortRepository.findByCode(code).orElseThrow();
        return statusRepository.findByResortId(resort.getId()).orElseThrow();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
