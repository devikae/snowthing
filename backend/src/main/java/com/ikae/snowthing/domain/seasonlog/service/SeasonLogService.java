package com.ikae.snowthing.domain.seasonlog.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonLogResponse;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonResponse;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonShareTokenResponse;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonSummaryResponse;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonVisitCreateRequest;
import com.ikae.snowthing.domain.seasonlog.dto.SeasonVisitResponse;
import com.ikae.snowthing.domain.seasonlog.dto.SharedSeasonResponse;
import com.ikae.snowthing.domain.seasonlog.entity.SeasonShare;
import com.ikae.snowthing.domain.seasonlog.entity.SeasonVisit;
import com.ikae.snowthing.domain.seasonlog.model.Season;
import com.ikae.snowthing.domain.seasonlog.model.SeasonSummary;
import com.ikae.snowthing.domain.seasonlog.repository.SeasonShareRepository;
import com.ikae.snowthing.domain.seasonlog.repository.SeasonVisitRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/** 시즌 방문 기록과 결산 공유. 설계: docs/conception/028_season_log/01_design.md */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SeasonLogService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final SeasonVisitRepository visitRepository;
    private final SeasonShareRepository shareRepository;
    private final ResortRepository resortRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public SeasonLogResponse getMySeason(Long memberId, Integer seasonStartYear) {
        LocalDate today = today();
        Season latest = Season.latest(today);
        Season season = seasonStartYear == null ? latest : viewableSeason(seasonStartYear, latest);
        List<SeasonVisit> visits = findVisits(memberId, season);
        String shareToken =
                shareRepository
                        .findByMember_IdAndSeasonStartYear(memberId, season.startYear())
                        .map(SeasonShare::getShareToken)
                        .orElse(null);
        return new SeasonLogResponse(
                SeasonResponse.from(season),
                latest.startYear(),
                today,
                latest.previous().startDate(),
                visits.stream().map(SeasonVisitResponse::from).toList(),
                SeasonSummaryResponse.from(SeasonSummary.of(visits)),
                shareToken);
    }

    @Transactional
    public SeasonVisitResponse addVisit(Long memberId, SeasonVisitCreateRequest request) {
        LocalDate today = today();
        LocalDate visitDate = request.visitDate();
        if (visitDate.isAfter(today)) {
            throw new CustomException(ErrorCode.SEASON_VISIT_FUTURE_DATE);
        }
        Season season =
                Season.containing(visitDate)
                        .orElseThrow(
                                () -> new CustomException(ErrorCode.SEASON_VISIT_OUT_OF_SEASON));
        if (season.startYear() < Season.latest(today).previous().startYear()) {
            throw new CustomException(ErrorCode.SEASON_VISIT_TOO_OLD);
        }
        // 흔한 중복(같은 날 다시 누름)은 쓰기 전에 알려 준다. 동시에 들어온 요청은 아래 유니크 제약이 막는다.
        if (visitRepository.existsByMember_IdAndVisitDate(memberId, visitDate)) {
            throw new CustomException(ErrorCode.SEASON_VISIT_DUPLICATED);
        }
        Resort resort =
                resortRepository
                        .findById(request.resortId())
                        .filter(Resort::isActive)
                        .orElseThrow(() -> new CustomException(ErrorCode.RESORT_NOT_FOUND));
        Member member = memberRepository.getReferenceById(memberId);
        SeasonVisit visit = new SeasonVisit(member, resort, visitDate, now());
        try {
            visitRepository.saveAndFlush(visit);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomException(ErrorCode.SEASON_VISIT_DUPLICATED, exception);
        }
        return SeasonVisitResponse.from(visit);
    }

    @Transactional
    public void deleteVisit(Long memberId, LocalDate visitDate) {
        if (visitRepository.deleteByMemberAndDate(memberId, visitDate) == 0) {
            throw new CustomException(ErrorCode.SEASON_VISIT_NOT_FOUND);
        }
    }

    /** 이미 켜져 있으면 같은 토큰을 돌려준다. 회원 행을 잠가 같은 회원의 동시 요청이 토큰을 두 번 만들지 않게 한다. */
    @Transactional
    public SeasonShareTokenResponse enableShare(Long memberId, int seasonStartYear) {
        Season season = viewableSeason(seasonStartYear, Season.latest(today()));
        Member member =
                memberRepository
                        .findByIdForUpdate(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        SeasonShare share =
                shareRepository
                        .findByMember_IdAndSeasonStartYear(memberId, season.startYear())
                        .orElseGet(
                                () ->
                                        shareRepository.save(
                                                new SeasonShare(
                                                        member, season.startYear(), now())));
        return new SeasonShareTokenResponse(share.getShareToken());
    }

    @Transactional
    public void disableShare(Long memberId, int seasonStartYear) {
        shareRepository
                .findByMember_IdAndSeasonStartYear(memberId, seasonStartYear)
                .ifPresent(shareRepository::delete);
    }

    public SharedSeasonResponse getSharedSeason(String shareToken) {
        if (!isUuid(shareToken)) {
            throw new CustomException(ErrorCode.SEASON_SHARE_NOT_FOUND);
        }
        SeasonShare share =
                shareRepository
                        .findByShareTokenWithMember(shareToken)
                        .filter(found -> found.getMember().getStatus() == MemberStatus.ACTIVE)
                        .orElseThrow(() -> new CustomException(ErrorCode.SEASON_SHARE_NOT_FOUND));
        Season season = new Season(share.getSeasonStartYear());
        Member member = share.getMember();
        return SharedSeasonResponse.of(
                member.getNickname(), season, SeasonSummary.of(findVisits(member.getId(), season)));
    }

    private List<SeasonVisit> findVisits(Long memberId, Season season) {
        return visitRepository.findInRange(memberId, season.startDate(), season.endDate());
    }

    private Season viewableSeason(int startYear, Season latest) {
        if (startYear < Season.MIN_START_YEAR || startYear > latest.startYear()) {
            throw new CustomException(ErrorCode.SEASON_INVALID);
        }
        return new Season(startYear);
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(KST));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(KST));
    }
}
