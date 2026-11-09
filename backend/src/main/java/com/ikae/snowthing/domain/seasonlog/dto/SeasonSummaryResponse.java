package com.ikae.snowthing.domain.seasonlog.dto;

import java.time.LocalDate;
import java.util.List;

import com.ikae.snowthing.domain.seasonlog.model.SeasonSummary;

/** 본인 화면용 결산. 첫 날과 마지막 날이 들어 있어 공개 응답에는 쓰지 않는다. */
public record SeasonSummaryResponse(
        int totalDays,
        LocalDate firstDate,
        LocalDate lastDate,
        ResortCountResponse topResort,
        List<ResortCountResponse> resorts) {
    public static SeasonSummaryResponse from(SeasonSummary summary) {
        return new SeasonSummaryResponse(
                summary.totalDays(),
                summary.firstDate(),
                summary.lastDate(),
                summary.topResort(),
                summary.resorts());
    }
}
