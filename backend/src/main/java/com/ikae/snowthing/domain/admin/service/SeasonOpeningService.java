package com.ikae.snowthing.domain.admin.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.SeasonOpeningItem;
import com.ikae.snowthing.domain.admin.dto.SeasonOpeningResponse;
import com.ikae.snowthing.domain.admin.dto.SeasonOpeningUpdateRequest;
import com.ikae.snowthing.domain.admin.entity.SeasonOpening;
import com.ikae.snowthing.domain.admin.entity.SystemConfig;
import com.ikae.snowthing.domain.admin.log.AdminActionLogger;
import com.ikae.snowthing.domain.admin.log.AdminActionType;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.admin.repository.SeasonOpeningRepository;
import com.ikae.snowthing.domain.admin.repository.SystemConfigRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 리조트 개장일과 메인 개장 배너 스위치 (IKA-87).
 *
 * <ul>
 *   <li>스위치는 system_config의 {@link #KEY_BANNER_ENABLED} 한 줄에 둔다. 줄이 없으면 켜진 것으로 본다. 예전 상수 배너는 늘 보였다.
 *   <li>조회는 캐시 없이 DB에서 읽는다. 줄 수가 리조트 수(10개 안팎)라 가볍고, 관리자가 바꾼 값이 새로고침에 바로 보여야 한다.
 *   <li>저장은 목록 전체를 한 번에 받는다. 화면이 순서를 맞바꾸므로 한 줄씩 저장하면 중간에 순서가 겹친다.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonOpeningService {

    public static final String KEY_BANNER_ENABLED = "SEASON_OPENING_BANNER_ENABLED";
    static final String TARGET_ID = "ALL";
    private static final LocalDate MIN_DATE = LocalDate.of(2020, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2100, 12, 31);
    private static final Comparator<SeasonOpening> BANNER_ORDER =
            Comparator.comparingInt(SeasonOpening::getBannerOrder)
                    .thenComparing(
                            SeasonOpening::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final SeasonOpeningRepository repository;
    private final SystemConfigRepository configRepository;
    private final AdminActionLogger actionLogger;

    @Transactional(readOnly = true)
    public SeasonOpeningResponse getSeasonOpenings() {
        return response(repository.findAllByOrderByBannerOrderAscIdAsc());
    }

    @Transactional
    public SeasonOpeningResponse update(SeasonOpeningUpdateRequest request, String updatedBy) {
        List<SeasonOpening> saved =
                new ArrayList<>(repository.findAllForUpdateByOrderByBannerOrderAscIdAsc());
        validate(request, saved);

        Map<String, SeasonOpening> byCode =
                saved.stream()
                        .collect(
                                Collectors.toMap(
                                        SeasonOpening::getResortCode, Function.identity()));
        List<String> changed = new ArrayList<>();
        for (SeasonOpeningUpdateRequest.Item item : request.resorts()) {
            SeasonOpening opening = byCode.get(item.resortCode());
            if (opening.update(
                    item.openingDate(),
                    item.confirmed(),
                    item.showOnBanner(),
                    item.bannerOrder())) {
                changed.add(item.resortCode());
            }
        }
        boolean switchChanged = updateBannerSwitch(request.bannerEnabled(), updatedBy);

        if (switchChanged || !changed.isEmpty()) {
            actionLogger.record(
                    AdminActionType.SEASON_OPENING_UPDATE,
                    AdminTargetType.SEASON_OPENING,
                    TARGET_ID,
                    reason(request.bannerEnabled(), changed));
        }
        log.info(
                "Season openings updated: bannerEnabled={}, changed={}, by={}",
                request.bannerEnabled(),
                changed,
                updatedBy);
        saved.sort(BANNER_ORDER);
        return response(saved);
    }

    private SeasonOpeningResponse response(List<SeasonOpening> openings) {
        return new SeasonOpeningResponse(
                isBannerEnabled(),
                List.copyOf(openings.stream().map(SeasonOpeningItem::from).toList()));
    }

    private boolean isBannerEnabled() {
        return configRepository
                .findById(KEY_BANNER_ENABLED)
                .map(config -> Boolean.parseBoolean(config.getValue()))
                .orElse(true);
    }

    /** 스위치 값이 바뀌었으면 true. 줄이 없으면 만든다. */
    private boolean updateBannerSwitch(boolean enabled, String updatedBy) {
        String value = String.valueOf(enabled);
        return configRepository
                .findById(KEY_BANNER_ENABLED)
                .map(
                        config -> {
                            if (value.equals(config.getValue())) {
                                return false;
                            }
                            config.update(value, updatedBy);
                            return true;
                        })
                .orElseGet(
                        () -> {
                            configRepository.save(
                                    new SystemConfig(
                                            KEY_BANNER_ENABLED,
                                            value,
                                            "BOOLEAN",
                                            "메인 개장 D-DAY 배너 표시 여부",
                                            updatedBy));
                            return true;
                        });
    }

    private static void validate(SeasonOpeningUpdateRequest request, List<SeasonOpening> saved) {
        Set<String> savedCodes =
                saved.stream().map(SeasonOpening::getResortCode).collect(Collectors.toSet());
        Set<String> requestCodes = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        for (SeasonOpeningUpdateRequest.Item item : request.resorts()) {
            if (!requestCodes.add(item.resortCode())) {
                throw new CustomException(ErrorCode.SEASON_OPENING_RESORT_MISMATCH);
            }
            if (!orders.add(item.bannerOrder())) {
                throw new CustomException(ErrorCode.SEASON_OPENING_ORDER_DUPLICATED);
            }
            LocalDate date = item.openingDate();
            if (date != null && (date.isBefore(MIN_DATE) || date.isAfter(MAX_DATE))) {
                throw new CustomException(ErrorCode.SEASON_OPENING_DATE_OUT_OF_RANGE);
            }
            if (item.showOnBanner() && date == null) {
                throw new CustomException(ErrorCode.SEASON_OPENING_DATE_REQUIRED);
            }
        }
        if (!requestCodes.equals(savedCodes)) {
            throw new CustomException(ErrorCode.SEASON_OPENING_RESORT_MISMATCH);
        }
    }

    private static String reason(boolean bannerEnabled, List<String> changed) {
        String state = bannerEnabled ? "배너 켬" : "배너 끔";
        return changed.isEmpty() ? state : state + ", 바뀐 리조트 " + String.join(", ", changed);
    }
}
