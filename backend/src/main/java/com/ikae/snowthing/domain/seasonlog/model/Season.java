package com.ikae.snowthing.domain.seasonlog.model;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Optional;

/**
 * 국내 스키 시즌 하나. 시작 연도로 구분한다(2026이면 2026-11-01부터 2027-04-30까지, "2026-27 시즌").
 *
 * <p>빠른 리조트가 11월 중순에 열고 늦은 리조트가 4월 초에 닫는다. 앞뒤로 여유를 둔 범위다. 설계:
 * docs/conception/028_season_log/01_design.md
 */
public record Season(int startYear) {

    public static final MonthDay START = MonthDay.of(11, 1);
    public static final MonthDay END = MonthDay.of(4, 30);
    public static final int MIN_START_YEAR = 2000;

    /** 날짜가 속한 시즌. 5월부터 10월은 시즌이 아니다. */
    public static Optional<Season> containing(LocalDate date) {
        int year = date.getYear();
        if (!date.isBefore(START.atYear(year))) {
            return Optional.of(new Season(year));
        }
        if (!date.isAfter(END.atYear(year))) {
            return Optional.of(new Season(year - 1));
        }
        return Optional.empty();
    }

    /** 오늘 기준 최근 시즌. 시즌 중이면 그 시즌, 비시즌이면 막 끝난 시즌. */
    public static Season latest(LocalDate today) {
        return containing(today).orElseGet(() -> new Season(today.getYear() - 1));
    }

    public LocalDate startDate() {
        return START.atYear(startYear);
    }

    public LocalDate endDate() {
        return END.atYear(startYear + 1);
    }

    public Season previous() {
        return new Season(startYear - 1);
    }

    public String label() {
        return String.format("%d-%02d 시즌", startYear, (startYear + 1) % 100);
    }
}
