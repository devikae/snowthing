package com.ikae.snowthing.domain.slope.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.ResortCountry;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.Compare;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.CompareItem;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.Connection;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.Neighbor;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.ResortDetail;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.ResortInfo;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.ResortList;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.ResortSummary;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.Similar;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.SlopeDetail;
import com.ikae.snowthing.domain.slope.dto.SlopeResponses.SlopeItem;
import com.ikae.snowthing.domain.slope.entity.Slope;
import com.ikae.snowthing.domain.slope.entity.SlopeConnection;
import com.ikae.snowthing.domain.slope.entity.SlopeDifficulty;
import com.ikae.snowthing.domain.slope.repository.SlopeConnectionRepository;
import com.ikae.snowthing.domain.slope.repository.SlopeRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/** 슬로프 제원 비교 조회. 모두 공개 읽기 전용이고 외부 호출이 없다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SlopeQueryService {

    public static final int SIMILAR_LIMIT = 5;
    public static final int COMPARE_MIN = 2;
    public static final int COMPARE_MAX = 3;

    /** 국내 먼저, 나라 안에서는 표시 순서, 같으면 ID. */
    private static final Comparator<Resort> RESORT_ORDER =
            Comparator.comparing(Resort::getCountry)
                    .thenComparingInt(Resort::getDisplayOrder)
                    .thenComparing(Resort::getId);

    private final ResortRepository resortRepository;
    private final SlopeRepository slopeRepository;
    private final SlopeConnectionRepository connectionRepository;

    /** 쿼리 세 번: 보이는 리조트별 슬로프 수, 그 리조트, 그 리조트들의 슬로프. */
    public ResortList getResorts() {
        Map<Long, Long> counts =
                slopeRepository.countByActiveResort().stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        List<Resort> resorts =
                resortRepository.findAllById(counts.keySet()).stream()
                        .filter(Resort::isSlopeSpecVisible)
                        .sorted(RESORT_ORDER)
                        .toList();
        Map<Long, List<Slope>> slopesByResort =
                slopeRepository
                        .findAllByResortIdIn(resorts.stream().map(Resort::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(slope -> slope.getResort().getId()));
        return new ResortList(
                resorts.stream()
                        .map(
                                resort -> {
                                    List<Slope> slopes =
                                            slopesByResort.getOrDefault(resort.getId(), List.of());
                                    return new ResortSummary(
                                            resort.getCode(),
                                            resort.getName(),
                                            resort.getRegionName(),
                                            resort.getCountry(),
                                            counts.get(resort.getId()),
                                            longest(slopes),
                                            steepest(slopes));
                                })
                        .toList());
    }

    public ResortDetail getResort(String resortCode) {
        Resort resort = requireResort(resortCode);
        List<Slope> slopes = slopeRepository.findAllByResortIdIn(List.of(resort.getId()));
        if (slopes.isEmpty()) {
            throw new CustomException(ErrorCode.RESORT_NOT_FOUND);
        }
        List<Connection> connections =
                connectionRepository
                        .findAllBySlopeIdIn(slopes.stream().map(Slope::getId).toList())
                        .stream()
                        .map(
                                connection ->
                                        new Connection(
                                                connection.getSlope().getId(),
                                                connection.getRelatedSlope().getId(),
                                                connection.getRelation(),
                                                connection.getDistanceM()))
                        .toList();
        return new ResortDetail(
                ResortInfo.from(resort),
                slopes.stream().map(SlopeItem::from).toList(),
                connections);
    }

    /** 쿼리 세 번: 슬로프와 리조트, 주변 관계, 반대 나라 후보 전체(수백 건). */
    public SlopeDetail getSlope(Long slopeId) {
        Slope slope =
                slopeRepository
                        .findActiveWithResort(slopeId)
                        .orElseThrow(() -> new CustomException(ErrorCode.SLOPE_NOT_FOUND));
        List<Neighbor> neighbors =
                connectionRepository.findAllBySlopeIdIn(List.of(slope.getId())).stream()
                        .map(SlopeQueryService::toNeighbor)
                        .toList();
        ResortCountry other =
                slope.getResort().getCountry() == ResortCountry.KR
                        ? ResortCountry.JP
                        : ResortCountry.KR;
        List<Similar> similar =
                SimilarSlopeFinder.find(
                                slope, slopeRepository.findAllActiveByCountry(other), SIMILAR_LIMIT)
                        .stream()
                        .map(Similar::from)
                        .toList();
        return new SlopeDetail(
                ResortInfo.from(slope.getResort()), SlopeItem.from(slope), neighbors, similar);
    }

    public Compare compare(List<String> resortCodes) {
        Set<String> codes = new LinkedHashSet<>(resortCodes);
        if (codes.size() < COMPARE_MIN || codes.size() > COMPARE_MAX) {
            throw new CustomException(ErrorCode.INVALID_SLOPE_COMPARE);
        }
        List<Resort> resorts = codes.stream().map(this::requireResort).toList();
        Map<Long, List<Slope>> slopesByResort =
                slopeRepository
                        .findAllByResortIdIn(resorts.stream().map(Resort::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(slope -> slope.getResort().getId()));
        return new Compare(
                resorts.stream()
                        .map(
                                resort ->
                                        toCompareItem(
                                                resort,
                                                slopesByResort.getOrDefault(
                                                        resort.getId(), List.of())))
                        .toList());
    }

    private Resort requireResort(String resortCode) {
        return resortRepository
                .findByCode(resortCode)
                .filter(Resort::isSlopeSpecVisible)
                .orElseThrow(() -> new CustomException(ErrorCode.RESORT_NOT_FOUND));
    }

    private static Neighbor toNeighbor(SlopeConnection connection) {
        Slope related = connection.getRelatedSlope();
        return new Neighbor(
                related.getId(),
                related.getName(),
                related.getDifficulty(),
                connection.getRelation(),
                connection.getDistanceM());
    }

    private static CompareItem toCompareItem(Resort resort, List<Slope> slopes) {
        Map<SlopeDifficulty, Integer> difficultyCounts = new EnumMap<>(SlopeDifficulty.class);
        for (SlopeDifficulty difficulty : SlopeDifficulty.values()) {
            difficultyCounts.put(difficulty, 0);
        }
        slopes.forEach(slope -> difficultyCounts.merge(slope.getDifficulty(), 1, Integer::sum));
        List<BigDecimal> avgGradients =
                slopes.stream().map(Slope::getAvgGradient).filter(Objects::nonNull).toList();
        BigDecimal meanAvg =
                avgGradients.isEmpty()
                        ? null
                        : avgGradients.stream()
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(
                                        BigDecimal.valueOf(avgGradients.size()),
                                        1,
                                        RoundingMode.HALF_UP);
        List<Integer> lengths =
                slopes.stream().map(Slope::getLengthM).filter(Objects::nonNull).toList();
        return new CompareItem(
                ResortInfo.from(resort),
                slopes.size(),
                lengths.isEmpty() ? null : lengths.stream().mapToInt(Integer::intValue).sum(),
                longest(slopes),
                steepest(slopes),
                meanAvg,
                avgGradients.size(),
                difficultyCounts);
    }

    private static Integer longest(List<Slope> slopes) {
        return slopes.stream()
                .map(Slope::getLengthM)
                .filter(Objects::nonNull)
                .max(Integer::compare)
                .orElse(null);
    }

    private static BigDecimal steepest(List<Slope> slopes) {
        return slopes.stream()
                .map(Slope::getMaxGradient)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }
}
