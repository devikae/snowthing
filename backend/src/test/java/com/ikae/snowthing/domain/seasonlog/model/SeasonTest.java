package com.ikae.snowthing.domain.seasonlog.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.seasonlog.dto.ResortCountResponse;
import com.ikae.snowthing.domain.seasonlog.entity.SeasonVisit;

class SeasonTest {

    @Test
    void 시즌은_11월_1일에_시작해_이듬해_4월_30일에_끝난다() {
        assertThat(Season.containing(LocalDate.of(2026, 10, 31))).isEmpty();
        assertThat(Season.containing(LocalDate.of(2026, 11, 1))).contains(new Season(2026));
        assertThat(Season.containing(LocalDate.of(2026, 12, 31))).contains(new Season(2026));
        assertThat(Season.containing(LocalDate.of(2027, 1, 1))).contains(new Season(2026));
        assertThat(Season.containing(LocalDate.of(2027, 4, 30))).contains(new Season(2026));
        assertThat(Season.containing(LocalDate.of(2027, 5, 1))).isEmpty();

        Season season = new Season(2026);
        assertThat(season.startDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(season.endDate()).isEqualTo(LocalDate.of(2027, 4, 30));
        assertThat(season.label()).isEqualTo("2026-27 시즌");
        assertThat(new Season(2099).label()).isEqualTo("2099-00 시즌");
    }

    @Test
    void 최근_시즌은_시즌_중이면_그_시즌이고_비시즌이면_막_끝난_시즌이다() {
        assertThat(Season.latest(LocalDate.of(2026, 11, 9))).isEqualTo(new Season(2026));
        assertThat(Season.latest(LocalDate.of(2027, 3, 1))).isEqualTo(new Season(2026));
        assertThat(Season.latest(LocalDate.of(2027, 7, 1))).isEqualTo(new Season(2026));
        assertThat(Season.latest(LocalDate.of(2027, 10, 31))).isEqualTo(new Season(2026));
        assertThat(Season.latest(LocalDate.of(2027, 11, 1))).isEqualTo(new Season(2027));
    }

    @Test
    void 결산은_횟수_많은_순이고_같으면_리조트_표시_순서를_따른다() {
        Resort high1 = resort(1L, "하이원리조트", 3);
        Resort phoenix = resort(2L, "휘닉스파크", 1);
        Resort vivaldi = resort(3L, "비발디파크", 2);
        List<SeasonVisit> visits =
                List.of(
                        visit(high1, LocalDate.of(2026, 12, 1)),
                        visit(phoenix, LocalDate.of(2026, 12, 5)),
                        visit(high1, LocalDate.of(2026, 12, 6)),
                        visit(vivaldi, LocalDate.of(2027, 1, 2)),
                        visit(phoenix, LocalDate.of(2027, 2, 14)));

        SeasonSummary summary = SeasonSummary.of(visits);

        assertThat(summary.totalDays()).isEqualTo(5);
        assertThat(summary.firstDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(summary.lastDate()).isEqualTo(LocalDate.of(2027, 2, 14));
        // 하이원과 휘닉스가 2일로 같다. 표시 순서가 앞선 휘닉스가 최다 리조트다.
        assertThat(summary.topResort()).isEqualTo(new ResortCountResponse(2L, "휘닉스파크", 2));
        assertThat(summary.resorts())
                .extracting(ResortCountResponse::resortName)
                .containsExactly("휘닉스파크", "하이원리조트", "비발디파크");
    }

    @Test
    void 기록이_없으면_빈_결산이다() {
        SeasonSummary summary = SeasonSummary.of(List.of());

        assertThat(summary.totalDays()).isZero();
        assertThat(summary.topResort()).isNull();
        assertThat(summary.resorts()).isEmpty();
    }

    private static Resort resort(Long id, String name, int displayOrder) {
        Resort resort =
                Resort.builder()
                        .name(name)
                        .regionName("강원")
                        .code("R" + id)
                        .displayOrder(displayOrder)
                        .active(true)
                        .build();
        ReflectionTestUtils.setField(resort, "id", id);
        return resort;
    }

    private static SeasonVisit visit(Resort resort, LocalDate date) {
        return new SeasonVisit(
                Member.builder().nickname("눈꽃보더").build(),
                resort,
                date,
                LocalDateTime.of(2027, 1, 1, 0, 0));
    }
}
