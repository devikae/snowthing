package com.ikae.snowthing.domain.slopestatus.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.ikae.snowthing.domain.slopestatus.source.SessionStatus;

/** 슬로프 현황판. 활성 리조트를 표시 순서대로 모두 담는다. */
public record SlopeStatusBoardResponse(List<ResortSlopes> resorts) {

    /**
     * @param supported 공식 현황을 수집할 수 있는 리조트인지. false면 sourceUrl부터 아래가 모두 비어 있다
     * @param collectedAt 마지막 성공 시각. 한 번도 성공하지 못했으면 null(화면은 "정보 준비 중")
     * @param stale 마지막 시도가 실패해 이전 성공 값을 보여 주는 중이면 true
     */
    public record ResortSlopes(
            String resortCode,
            String resortName,
            String regionName,
            boolean supported,
            String sourceUrl,
            OffsetDateTime collectedAt,
            OffsetDateTime lastAttemptAt,
            boolean stale,
            Integer openSlopeCount,
            Integer totalSlopeCount,
            String operatingNote,
            List<Slope> slopes) {}

    /** 출처에 없는 시간대는 null이다. */
    public record Slope(
            String name,
            String difficulty,
            boolean open,
            SessionStatus day,
            SessionStatus night,
            SessionStatus lateNight,
            String note) {}
}
