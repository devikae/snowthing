package com.ikae.snowthing.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.admin.dto.SeasonOpeningResponse;
import com.ikae.snowthing.domain.admin.dto.SeasonOpeningUpdateRequest;
import com.ikae.snowthing.domain.admin.dto.SeasonOpeningUpdateRequest.Item;
import com.ikae.snowthing.domain.admin.entity.SeasonOpening;
import com.ikae.snowthing.domain.admin.entity.SystemConfig;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.admin.repository.SeasonOpeningRepository;
import com.ikae.snowthing.domain.admin.repository.SystemConfigRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class SeasonOpeningServiceTest {

    private static final LocalDate NOV_22 = LocalDate.of(2026, 11, 22);
    private static final LocalDate NOV_24 = LocalDate.of(2026, 11, 24);

    @Mock private SeasonOpeningRepository repository;
    @Mock private SystemConfigRepository configRepository;
    @Mock private AdminActionLogger actionLogger;
    @InjectMocks private SeasonOpeningService service;

    private SeasonOpening yongpyong;
    private SeasonOpening high1;
    private SystemConfig bannerSwitch;

    @BeforeEach
    void setUp() {
        yongpyong = opening(1L, "YONGPYONG", "모나용평", NOV_22, true, 1);
        high1 = opening(2L, "HIGH1", "하이원", NOV_24, true, 2);
        bannerSwitch =
                new SystemConfig(
                        SeasonOpeningService.KEY_BANNER_ENABLED,
                        "true",
                        "BOOLEAN",
                        "메인 개장 D-DAY 배너 표시 여부",
                        "SYSTEM");
        // 문구 줄은 기본 문구로 이미 있다고 둔다. 문구를 보내지 않는 저장은 문구를 바꾸지 않는다.
        lenient()
                .when(configRepository.findById(SeasonOpeningService.KEY_BANNER_TITLE))
                .thenReturn(
                        Optional.of(
                                new SystemConfig(
                                        SeasonOpeningService.KEY_BANNER_TITLE,
                                        SeasonOpeningService.DEFAULT_BANNER_TITLE,
                                        "STRING",
                                        "메인 개장 D-DAY 배너 맨 앞 문구",
                                        "SYSTEM")));
        lenient()
                .when(repository.findAllByOrderByBannerOrderAscIdAsc())
                .thenReturn(new ArrayList<>(List.of(yongpyong, high1)));
        lenient()
                .when(repository.findAllForUpdateByOrderByBannerOrderAscIdAsc())
                .thenReturn(new ArrayList<>(List.of(yongpyong, high1)));
    }

    @Test
    @DisplayName("조회는 스위치 값과 리조트를 배너 순서대로 준다")
    void readsSwitchAndResorts() {
        when(configRepository.findById(SeasonOpeningService.KEY_BANNER_ENABLED))
                .thenReturn(Optional.of(bannerSwitch));

        SeasonOpeningResponse response = service.getSeasonOpenings();

        assertThat(response.bannerEnabled()).isTrue();
        assertThat(response.resorts())
                .extracting("resortCode", "name", "openingDate", "showOnBanner", "bannerOrder")
                .containsExactly(
                        tuple("YONGPYONG", "모나용평", NOV_22, true, 1),
                        tuple("HIGH1", "하이원", NOV_24, true, 2));
    }

    @Test
    @DisplayName("스위치 설정 줄이 없으면 배너는 켜진 것으로 본다")
    void missingSwitchMeansEnabled() {
        when(configRepository.findById(SeasonOpeningService.KEY_BANNER_ENABLED))
                .thenReturn(Optional.empty());

        assertThat(service.getSeasonOpenings().bannerEnabled()).isTrue();
    }

    @Test
    @DisplayName("저장하면 날짜, 표시, 순서를 바꾸고 바뀐 리조트만 조치 기록에 남긴다")
    void updatesAndRecordsChangedResorts() {
        when(configRepository.findById(SeasonOpeningService.KEY_BANNER_ENABLED))
                .thenReturn(Optional.of(bannerSwitch));
        LocalDate dec1 = LocalDate.of(2026, 12, 1);

        SeasonOpeningResponse response =
                service.update(
                        request(
                                false,
                                new Item("YONGPYONG", NOV_22, true, true, 2),
                                new Item("HIGH1", dec1, false, false, 1)),
                        "admin@snowthing.com");

        assertThat(high1.getOpeningDate()).isEqualTo(dec1);
        assertThat(high1.isConfirmed()).isFalse();
        assertThat(high1.isShowOnBanner()).isFalse();
        assertThat(yongpyong.getBannerOrder()).isEqualTo(2);
        assertThat(bannerSwitch.getValue()).isEqualTo("false");
        assertThat(response.resorts())
                .extracting("resortCode")
                .containsExactly("HIGH1", "YONGPYONG");
        verify(actionLogger)
                .record(
                        AdminActionType.SEASON_OPENING_UPDATE,
                        AdminTargetType.SEASON_OPENING,
                        "ALL",
                        "배너 끔, 바뀐 리조트 YONGPYONG, HIGH1");
    }

    @Test
    @DisplayName("바뀐 값이 없으면 조치 기록을 남기지 않는다")
    void noChangeNoLog() {
        when(configRepository.findById(SeasonOpeningService.KEY_BANNER_ENABLED))
                .thenReturn(Optional.of(bannerSwitch));

        service.update(
                request(
                        true,
                        new Item("YONGPYONG", NOV_22, true, true, 1),
                        new Item("HIGH1", NOV_24, true, true, 2)),
                "admin");

        verify(actionLogger, never()).record(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("배너 문구를 바꾸면 공백을 정리해 저장하고 조치 기록에 남긴다")
    void updatesBannerTitle() {
        when(configRepository.findById(SeasonOpeningService.KEY_BANNER_ENABLED))
                .thenReturn(Optional.of(bannerSwitch));

        SeasonOpeningResponse response =
                service.update(
                        new SeasonOpeningUpdateRequest(
                                true,
                                "  개장   임박 ",
                                null,
                                List.of(
                                        new Item("YONGPYONG", NOV_22, true, true, 1),
                                        new Item("HIGH1", NOV_24, true, true, 2))),
                        "admin");

        assertThat(response.bannerTitle()).isEqualTo("개장 임박");
        verify(actionLogger)
                .record(
                        eq(AdminActionType.SEASON_OPENING_UPDATE),
                        eq(AdminTargetType.SEASON_OPENING),
                        eq("ALL"),
                        eq("배너 켬, 문구 \"개장 임박\""));
    }

    @Test
    @DisplayName("개장 후 표시 설정 줄이 없으면 꺼진 것으로 본다")
    void missingShowAfterOpeningMeansOff() {
        when(configRepository.findById(SeasonOpeningService.KEY_SHOW_AFTER_OPENING))
                .thenReturn(Optional.empty());

        assertThat(service.getSeasonOpenings().showAfterOpening()).isFalse();
    }

    @Test
    @DisplayName("개장 후 표시를 켜면 설정 줄을 만들고 조치 기록에 남긴다")
    void turnsOnShowAfterOpening() {
        when(configRepository.findById(SeasonOpeningService.KEY_BANNER_ENABLED))
                .thenReturn(Optional.of(bannerSwitch));
        SystemConfig[] stored = new SystemConfig[1];
        when(configRepository.findById(SeasonOpeningService.KEY_SHOW_AFTER_OPENING))
                .thenAnswer(invocation -> Optional.ofNullable(stored[0]));
        when(configRepository.save(any(SystemConfig.class)))
                .thenAnswer(
                        invocation -> {
                            stored[0] = invocation.getArgument(0);
                            return stored[0];
                        });

        SeasonOpeningResponse response =
                service.update(
                        new SeasonOpeningUpdateRequest(
                                true,
                                null,
                                true,
                                List.of(
                                        new Item("YONGPYONG", NOV_22, true, true, 1),
                                        new Item("HIGH1", NOV_24, true, true, 2))),
                        "admin");

        assertThat(stored[0].getValue()).isEqualTo("true");
        assertThat(response.showAfterOpening()).isTrue();
        verify(actionLogger)
                .record(
                        AdminActionType.SEASON_OPENING_UPDATE,
                        AdminTargetType.SEASON_OPENING,
                        "ALL",
                        "배너 켬, 개장 후 D+N 표시 켬");
    }

    @Test
    @DisplayName("개장 후 표시 값을 보내지 않으면(예전 관리자 화면) 저장된 값을 바꾸지 않는다")
    void keepsShowAfterOpeningWhenOmitted() {
        when(configRepository.findById(SeasonOpeningService.KEY_BANNER_ENABLED))
                .thenReturn(Optional.of(bannerSwitch));
        SystemConfig afterOpening =
                new SystemConfig(
                        SeasonOpeningService.KEY_SHOW_AFTER_OPENING,
                        "true",
                        "BOOLEAN",
                        "개장한 리조트도 개장 D+N으로 배너에 표시",
                        "SYSTEM");
        when(configRepository.findById(SeasonOpeningService.KEY_SHOW_AFTER_OPENING))
                .thenReturn(Optional.of(afterOpening));

        SeasonOpeningResponse response =
                service.update(
                        request(
                                true,
                                new Item("YONGPYONG", NOV_22, true, true, 1),
                                new Item("HIGH1", NOV_24, true, true, 2)),
                        "admin");

        assertThat(afterOpening.getValue()).isEqualTo("true");
        assertThat(response.showAfterOpening()).isTrue();
        verify(actionLogger, never()).record(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("스위치 설정 줄이 없으면 저장할 때 만든다")
    void createsSwitchWhenMissing() {
        when(configRepository.findById(SeasonOpeningService.KEY_BANNER_ENABLED))
                .thenReturn(Optional.empty());

        service.update(
                request(
                        false,
                        new Item("YONGPYONG", NOV_22, true, true, 1),
                        new Item("HIGH1", NOV_24, true, true, 2)),
                "admin");

        verify(configRepository)
                .save(
                        argThat(
                                (SystemConfig config) ->
                                        config.getKey()
                                                        .equals(
                                                                SeasonOpeningService
                                                                        .KEY_BANNER_ENABLED)
                                                && config.getValue().equals("false")));
        verify(actionLogger)
                .record(
                        eq(AdminActionType.SEASON_OPENING_UPDATE),
                        eq(AdminTargetType.SEASON_OPENING),
                        eq("ALL"),
                        eq("배너 끔"));
    }

    @Test
    @DisplayName("배너 순서가 겹치면 400이고 아무것도 바꾸지 않는다")
    void rejectsDuplicatedOrder() {
        assertRejected(
                request(
                        true,
                        new Item("YONGPYONG", NOV_22, true, true, 1),
                        new Item("HIGH1", NOV_24, true, true, 1)),
                ErrorCode.SEASON_OPENING_ORDER_DUPLICATED);
    }

    @Test
    @DisplayName("배너에 넣을 리조트에 개장일이 없으면 400")
    void rejectsBannerWithoutDate() {
        assertRejected(
                request(
                        true,
                        new Item("YONGPYONG", null, false, true, 1),
                        new Item("HIGH1", NOV_24, true, true, 2)),
                ErrorCode.SEASON_OPENING_DATE_REQUIRED);
    }

    @Test
    @DisplayName("개장일이 2020년부터 2100년 밖이면 400")
    void rejectsDateOutOfRange() {
        assertRejected(
                request(
                        true,
                        new Item("YONGPYONG", LocalDate.of(26, 11, 22), true, true, 1),
                        new Item("HIGH1", NOV_24, true, true, 2)),
                ErrorCode.SEASON_OPENING_DATE_OUT_OF_RANGE);
    }

    @Test
    @DisplayName("모르는 리조트, 빠진 리조트, 같은 리조트 두 번은 400")
    void rejectsResortMismatch() {
        assertRejected(
                request(
                        true,
                        new Item("YONGPYONG", NOV_22, true, true, 1),
                        new Item("JISAN", NOV_24, true, true, 2)),
                ErrorCode.SEASON_OPENING_RESORT_MISMATCH);
        assertRejected(
                request(true, new Item("YONGPYONG", NOV_22, true, true, 1)),
                ErrorCode.SEASON_OPENING_RESORT_MISMATCH);
        assertRejected(
                request(
                        true,
                        new Item("YONGPYONG", NOV_22, true, true, 1),
                        new Item("YONGPYONG", NOV_22, true, true, 2)),
                ErrorCode.SEASON_OPENING_RESORT_MISMATCH);
    }

    private void assertRejected(SeasonOpeningUpdateRequest request, ErrorCode expected) {
        assertThatThrownBy(() -> service.update(request, "admin"))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
        assertThat(yongpyong.getOpeningDate()).isEqualTo(NOV_22);
        assertThat(yongpyong.getBannerOrder()).isEqualTo(1);
        verify(actionLogger, never()).record(any(), any(), any(), any());
    }

    private static SeasonOpeningUpdateRequest request(boolean enabled, Item... items) {
        return new SeasonOpeningUpdateRequest(enabled, null, null, List.of(items));
    }

    private static SeasonOpening opening(
            Long id, String code, String name, LocalDate date, boolean banner, int order) {
        SeasonOpening opening =
                SeasonOpening.builder()
                        .resortCode(code)
                        .resortName(name)
                        .openingDate(date)
                        .confirmed(banner)
                        .showOnBanner(banner)
                        .bannerOrder(order)
                        .build();
        ReflectionTestUtils.setField(opening, "id", id);
        return opening;
    }
}
