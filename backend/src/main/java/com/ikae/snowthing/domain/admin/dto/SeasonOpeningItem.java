package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDate;

import com.ikae.snowthing.domain.admin.entity.SeasonOpening;

/** 리조트 한 곳의 개장일. openingDate가 null이면 화면은 D-DAY를 그리지 않는다. */
public record SeasonOpeningItem(
        String resortCode,
        String name,
        LocalDate openingDate,
        boolean confirmed,
        boolean showOnBanner,
        int bannerOrder) {

    public static SeasonOpeningItem from(SeasonOpening entity) {
        return new SeasonOpeningItem(
                entity.getResortCode(),
                entity.getResortName(),
                entity.getOpeningDate(),
                entity.isConfirmed(),
                entity.isShowOnBanner(),
                entity.getBannerOrder());
    }
}
