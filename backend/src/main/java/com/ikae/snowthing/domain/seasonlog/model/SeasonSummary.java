package com.ikae.snowthing.domain.seasonlog.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.seasonlog.dto.ResortCountResponse;
import com.ikae.snowthing.domain.seasonlog.entity.SeasonVisit;

/**
 * 시즌 결산. 한 시즌 기록은 많아야 181행이라 메모리에서 센다.
 *
 * <p>리조트 순서는 횟수 많은 순, 같으면 리조트 표시 순서, 리조트 ID 순이다. 같은 데이터면 최다 리조트가 늘 같다.
 */
public record SeasonSummary(
        int totalDays,
        LocalDate firstDate,
        LocalDate lastDate,
        ResortCountResponse topResort,
        List<ResortCountResponse> resorts) {

    /** visits는 날짜 오름차순이어야 한다. */
    public static SeasonSummary of(List<SeasonVisit> visits) {
        if (visits.isEmpty()) {
            return new SeasonSummary(0, null, null, null, List.of());
        }
        Map<Long, Resort> resortsById = new LinkedHashMap<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        for (SeasonVisit visit : visits) {
            Resort resort = visit.getResort();
            resortsById.putIfAbsent(resort.getId(), resort);
            counts.merge(resort.getId(), 1, Integer::sum);
        }
        List<ResortCountResponse> resorts =
                resortsById.values().stream()
                        .sorted(
                                Comparator.<Resort>comparingInt(
                                                resort -> -counts.get(resort.getId()))
                                        .thenComparingInt(Resort::getDisplayOrder)
                                        .thenComparing(Resort::getId))
                        .map(
                                resort ->
                                        new ResortCountResponse(
                                                resort.getId(),
                                                resort.getName(),
                                                counts.get(resort.getId())))
                        .toList();
        return new SeasonSummary(
                visits.size(),
                visits.getFirst().getVisitDate(),
                visits.getLast().getVisitDate(),
                resorts.getFirst(),
                resorts);
    }
}
