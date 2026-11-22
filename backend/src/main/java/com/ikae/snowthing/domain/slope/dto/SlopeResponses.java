package com.ikae.snowthing.domain.slope.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.ResortCountry;
import com.ikae.snowthing.domain.slope.entity.Slope;
import com.ikae.snowthing.domain.slope.entity.SlopeDifficulty;
import com.ikae.snowthing.domain.slope.entity.SlopeRelation;
import com.ikae.snowthing.domain.slope.service.SimilarSlopeFinder;

/** 슬로프 제원 비교 API 응답. 값이 없는 제원은 null로 내려 화면이 "미공개"로 표시한다. */
public final class SlopeResponses {

    private SlopeResponses() {}

    public record ResortInfo(String code, String name, String region, ResortCountry country) {
        public static ResortInfo from(Resort resort) {
            return new ResortInfo(
                    resort.getCode(),
                    resort.getName(),
                    resort.getRegionName(),
                    resort.getCountry());
        }
    }

    public record SlopeItem(
            Long id,
            String name,
            String nameLocal,
            String officialDifficulty,
            SlopeDifficulty difficulty,
            Integer lengthM,
            BigDecimal avgGradient,
            BigDecimal maxGradient,
            Integer widthM,
            String liftName,
            List<Long> osmWayIds,
            String sourceUrl,
            LocalDate checkedAt) {
        public static SlopeItem from(Slope slope) {
            return new SlopeItem(
                    slope.getId(),
                    slope.getName(),
                    slope.getNameLocal(),
                    slope.getOfficialDifficulty(),
                    slope.getDifficulty(),
                    slope.getLengthM(),
                    slope.getAvgGradient(),
                    slope.getMaxGradient(),
                    slope.getWidthM(),
                    slope.getLiftName(),
                    slope.osmWayIdList(),
                    slope.getSourceUrl(),
                    slope.getCheckedAt());
        }
    }

    /** 리조트 목록. 슬로프 제원이 있는 리조트만 나라별로 준다. */
    public record ResortList(List<ResortSummary> resorts) {}

    public record ResortSummary(
            String code,
            String name,
            String region,
            ResortCountry country,
            long slopeCount,
            Integer longestLengthM,
            BigDecimal steepestMaxGradient) {}

    /** 리조트 한 곳. connections는 리조트 안 모든 관계라 화면이 추가 요청 없이 코스 카드를 그린다. */
    public record ResortDetail(
            ResortInfo resort, List<SlopeItem> slopes, List<Connection> connections) {}

    public record Connection(
            Long slopeId, Long relatedSlopeId, SlopeRelation relation, Integer distanceM) {}

    /** 슬로프 한 곳. similarKr, similarJp는 나라별로 경사가 비슷한 후보다(자기 자신 제외). */
    public record SlopeDetail(
            ResortInfo resort,
            SlopeItem slope,
            List<Neighbor> neighbors,
            List<Similar> similarKr,
            List<Similar> similarJp) {}

    public record Neighbor(
            Long id,
            String name,
            SlopeDifficulty difficulty,
            SlopeRelation relation,
            Integer distanceM) {}

    public record Similar(
            ResortInfo resort,
            SlopeItem slope,
            SimilarSlopeFinder.Metric metric,
            BigDecimal baseGradient,
            BigDecimal gradient,
            BigDecimal gradientDiff,
            Integer lengthDiff) {
        public static Similar from(SimilarSlopeFinder.Match match) {
            return new Similar(
                    ResortInfo.from(match.slope().getResort()),
                    SlopeItem.from(match.slope()),
                    match.metric(),
                    match.baseGradient(),
                    match.gradient(),
                    match.gradientDiff(),
                    match.lengthDiff());
        }
    }
}
