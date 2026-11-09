package com.ikae.snowthing.domain.seasonlog.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 내 시즌 기록 화면 응답.
 *
 * @param latestSeasonStartYear 고를 수 있는 가장 최근 시즌
 * @param today KST 오늘. 화면이 기기 시계 대신 이 값으로 미래 날짜를 막는다
 * @param recordableFrom 기록할 수 있는 가장 이른 날짜(지난 시즌 시작일)
 * @param shareToken 공유가 꺼져 있으면 null
 */
public record SeasonLogResponse(
        SeasonResponse season,
        int latestSeasonStartYear,
        LocalDate today,
        LocalDate recordableFrom,
        List<SeasonVisitResponse> visits,
        SeasonSummaryResponse summary,
        String shareToken) {}
