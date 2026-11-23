package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 개장일 목록 한 번에 저장. 저장된 리조트 전부를 한 번씩 보내야 한다. 리조트 목록이 맞는지, 배너 순서가 겹치지 않는지, 배너에 넣을 리조트에 날짜가 있는지는 서비스가
 * 확인한다.
 */
public record SeasonOpeningUpdateRequest(
        @NotNull Boolean bannerEnabled,
        // 배너 맨 앞 문구. 비우거나 보내지 않으면 기본 문구("개장까지")로 둔다.
        @Size(max = SeasonOpeningUpdateRequest.BANNER_TITLE_MAX) String bannerTitle,
        // 개장한 리조트도 "개장 D+N"으로 배너에 남길지. 보내지 않으면(예전 관리자 화면) 저장된 값을 그대로 둔다.
        Boolean showAfterOpening,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid Item> resorts) {

    public record Item(
            @NotBlank @Size(max = 30) String resortCode,
            LocalDate openingDate,
            boolean confirmed,
            boolean showOnBanner,
            @Min(1) @Max(999) int bannerOrder) {}

    public static final int BANNER_TITLE_MAX = 12;
}
