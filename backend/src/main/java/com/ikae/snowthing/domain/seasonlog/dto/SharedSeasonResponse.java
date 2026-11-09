package com.ikae.snowthing.domain.seasonlog.dto;

import java.util.List;

import com.ikae.snowthing.domain.seasonlog.model.Season;
import com.ikae.snowthing.domain.seasonlog.model.SeasonSummary;

/** 공개 결산. 닉네임 말고는 회원 정보를 넣지 않는다. 언제 어디 있었는지 드러나는 날짜(첫 날, 마지막 날, 날짜 목록)도 넣지 않는다. */
public record SharedSeasonResponse(
        String nickname,
        int seasonStartYear,
        String seasonLabel,
        int totalDays,
        ResortCountResponse topResort,
        List<ResortCountResponse> resorts) {
    public static SharedSeasonResponse of(String nickname, Season season, SeasonSummary summary) {
        return new SharedSeasonResponse(
                nickname,
                season.startYear(),
                season.label(),
                summary.totalDays(),
                summary.topResort(),
                summary.resorts());
    }
}
