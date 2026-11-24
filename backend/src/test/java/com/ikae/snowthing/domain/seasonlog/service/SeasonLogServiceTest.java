package com.ikae.snowthing.domain.seasonlog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonVisitCreateRequest;
import com.ikae.snowthing.domain.seasonlog.repository.SeasonShareRepository;
import com.ikae.snowthing.domain.seasonlog.repository.SeasonVisitRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 시즌 기록의 날짜 경계 (IKA-118). 기준 문서: docs/conception/028_season_log/01_design.md.
 *
 * <p>"오늘"은 KST 기준이다. 서버 시계가 UTC여도 한국 자정이 지나면 그날을 기록할 수 있어야 한다. 시계를 고정해 경계를 확인한다. 저장소는 가짜다.
 */
class SeasonLogServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long RESORT_ID = 3L;

    private final SeasonVisitRepository visits = mock(SeasonVisitRepository.class);
    private final SeasonShareRepository shares = mock(SeasonShareRepository.class);
    private final ResortRepository resorts = mock(ResortRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);

    @Test
    @DisplayName("UTC로는 전날이어도 KST 자정이 지나면 그날을 기록할 수 있고, 내일은 거절한다")
    void todayIsKoreanDate() {
        // UTC 2026-12-05 15:30 = KST 2026-12-06 00:30
        SeasonLogService service = serviceAt("2026-12-05T15:30:00Z");

        assertThat(service.addVisit(MEMBER_ID, visit("2026-12-06")).visitDate())
                .isEqualTo(LocalDate.parse("2026-12-06"));
        assertRejected(service, "2026-12-07", ErrorCode.SEASON_VISIT_FUTURE_DATE);
    }

    @Test
    @DisplayName("KST 자정 1분 전에는 다음 날이 아직 미래다")
    void beforeKoreanMidnightTomorrowIsFuture() {
        // UTC 2026-12-05 14:59 = KST 2026-12-05 23:59
        SeasonLogService service = serviceAt("2026-12-05T14:59:00Z");

        assertThat(service.addVisit(MEMBER_ID, visit("2026-12-05"))).isNotNull();
        assertRejected(service, "2026-12-06", ErrorCode.SEASON_VISIT_FUTURE_DATE);
    }

    @Test
    @DisplayName("시즌은 11월 1일부터 4월 30일까지다. 10월 31일과 5월 1일은 거절한다")
    void seasonRange() {
        SeasonLogService service = serviceAt("2027-06-15T03:00:00Z");

        assertThat(service.addVisit(MEMBER_ID, visit("2026-11-01"))).isNotNull();
        assertThat(service.addVisit(MEMBER_ID, visit("2027-04-30"))).isNotNull();
        assertRejected(service, "2026-10-31", ErrorCode.SEASON_VISIT_OUT_OF_SEASON);
        assertRejected(service, "2027-05-01", ErrorCode.SEASON_VISIT_OUT_OF_SEASON);
    }

    @Test
    @DisplayName("시즌 중에는 이번 시즌과 지난 시즌까지만 기록한다. 그 전 시즌은 거절한다")
    void acceptsOnlyLatestAndPreviousSeasonInSeason() {
        SeasonLogService service = serviceAt("2026-12-06T03:00:00Z");

        assertThat(service.addVisit(MEMBER_ID, visit("2025-11-01"))).isNotNull();
        assertThat(service.addVisit(MEMBER_ID, visit("2026-04-30"))).isNotNull();
        assertRejected(service, "2025-04-30", ErrorCode.SEASON_VISIT_TOO_OLD);
    }

    @Test
    @DisplayName("비시즌(5~10월)에는 막 끝난 시즌이 최근 시즌이다. 그 전 시즌까지 받고 더 오래된 날짜는 거절한다")
    void offSeasonUsesTheSeasonThatJustEnded() {
        SeasonLogService service = serviceAt("2027-07-01T03:00:00Z");

        assertThat(service.addVisit(MEMBER_ID, visit("2027-04-30"))).isNotNull();
        assertThat(service.addVisit(MEMBER_ID, visit("2025-11-01"))).isNotNull();
        assertRejected(service, "2025-04-30", ErrorCode.SEASON_VISIT_TOO_OLD);
        // 조회와 공유도 같은 기준이다: 최근 시즌(2026)보다 뒤나 2000년 이전은 볼 수 없다.
        assertThatThrownBy(() -> service.getMySeason(MEMBER_ID, 2027))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEASON_INVALID);
        assertThatThrownBy(() -> service.enableShare(MEMBER_ID, 1999))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SEASON_INVALID);
    }

    @Test
    @DisplayName("같은 날짜 기록이 있으면 409이고, 비활성 리조트는 404다")
    void duplicateAndInactiveResort() {
        SeasonLogService service = serviceAt("2026-12-06T03:00:00Z");
        given(visits.existsByMember_IdAndVisitDate(MEMBER_ID, LocalDate.parse("2026-12-05")))
                .willReturn(true);
        Resort inactive =
                Resort.builder()
                        .name("닫은 리조트")
                        .regionName("테스트")
                        .code("SEASON_UNIT_INACTIVE")
                        .displayOrder(999)
                        .active(false)
                        .build();
        given(resorts.findById(99L)).willReturn(Optional.of(inactive));

        assertRejected(service, "2026-12-05", ErrorCode.SEASON_VISIT_DUPLICATED);
        assertThatThrownBy(
                        () ->
                                service.addVisit(
                                        MEMBER_ID,
                                        new SeasonVisitCreateRequest(
                                                LocalDate.parse("2026-12-04"), 99L)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESORT_NOT_FOUND);
    }

    @Test
    @DisplayName("공유 토큰이 UUID 모양이 아니면 DB를 보지 않고 404다")
    void malformedShareTokenNeverHitsDatabase() {
        SeasonLogService service = serviceAt("2026-12-06T03:00:00Z");

        for (String token :
                new String[] {
                    "",
                    "abc",
                    "123E4567-E89B-12D3-A456-426614174000",
                    "123e4567e89b12d3a456426614174000",
                    "123e4567-e89b-12d3-a456-42661417400g",
                    "123e4567-e89b-12d3-a456-4266141740000"
                }) {
            assertThatThrownBy(() -> service.getSharedSeason(token))
                    .as(token)
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.SEASON_SHARE_NOT_FOUND);
        }
        verifyNoInteractions(shares);
    }

    private void assertRejected(SeasonLogService service, String date, ErrorCode expected) {
        assertThatThrownBy(() -> service.addVisit(MEMBER_ID, visit(date)))
                .as(date)
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private static SeasonVisitCreateRequest visit(String date) {
        return new SeasonVisitCreateRequest(LocalDate.parse(date), RESORT_ID);
    }

    private SeasonLogService serviceAt(String utcInstant) {
        Resort resort =
                Resort.builder()
                        .name("테스트 리조트")
                        .regionName("테스트")
                        .code("SEASON_UNIT")
                        .displayOrder(998)
                        .active(true)
                        .build();
        given(resorts.findById(RESORT_ID)).willReturn(Optional.of(resort));
        given(visits.existsByMember_IdAndVisitDate(anyLong(), any())).willReturn(false);
        given(visits.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
        // 서버 시계는 UTC로 둔다. 서비스가 KST로 바꿔 읽어야 한다.
        Clock clock = Clock.fixed(Instant.parse(utcInstant), ZoneOffset.UTC);
        return new SeasonLogService(visits, shares, resorts, members, clock);
    }
}
