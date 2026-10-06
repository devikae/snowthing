package com.ikae.snowthing.domain.resortreport.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.ResortRepository;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportCreateRequest;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportResponse;
import com.ikae.snowthing.domain.resortreport.entity.ResortReport;
import com.ikae.snowthing.domain.resortreport.entity.ResortReportStatus;
import com.ikae.snowthing.domain.resortreport.repository.ResortReportRepository;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.common.dto.CursorPageResponse.PageInfo;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResortReportService {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAGE = 100;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DAILY_REPORT_LIMIT = 5;

    private final ResortReportRepository resortReportRepository;
    private final ResortRepository resortRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    @Transactional
    public ResortReportResponse createReport(Long memberId, ResortReportCreateRequest request) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST_ZONE));
        validateSeason(now.toLocalDate());

        Member author =
                memberRepository
                        .findByIdForUpdate(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        long todayCount =
                resortReportRepository
                        .countByAuthorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                                memberId, todayStart, todayStart.plusDays(1));
        if (todayCount >= DAILY_REPORT_LIMIT) {
            throw new CustomException(ErrorCode.RESORT_REPORT_DAILY_LIMIT_EXCEEDED);
        }

        Resort resort =
                resortRepository
                        .findById(request.getResortId())
                        .filter(Resort::isActive)
                        .orElseThrow(() -> new CustomException(ErrorCode.RESORT_NOT_FOUND));

        ResortReport report =
                ResortReport.builder()
                        .resort(resort)
                        .author(author)
                        .content(request.getContent())
                        .createdAt(now)
                        .build();

        ResortReport savedReport = resortReportRepository.save(report);
        return ResortReportResponse.from(savedReport, true);
    }

    public CursorPageResponse<ResortReportResponse> getTodayReports(
            Long resortId, int page, int size, CustomUserDetails userDetails) {
        validatePage(page, size);
        LocalDate today = LocalDate.now(clock.withZone(KST_ZONE));
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        Page<ResortReport> reports;
        if (resortId != null) {
            reports =
                    resortReportRepository.findTodayReportsByResortId(
                            resortId,
                            ResortReportStatus.NORMAL,
                            todayStart,
                            tomorrowStart,
                            PageRequest.of(page - 1, size));
        } else {
            reports =
                    resortReportRepository.findTodayReports(
                            ResortReportStatus.NORMAL,
                            todayStart,
                            tomorrowStart,
                            PageRequest.of(page - 1, size));
        }

        List<ResortReportResponse> content =
                reports.getContent().stream()
                        .map(
                                report ->
                                        ResortReportResponse.from(
                                                report, canDelete(report, userDetails)))
                        .toList();
        return new CursorPageResponse<>(
                content,
                PageInfo.ofOffset(
                        page,
                        reports.getTotalPages(),
                        reports.getTotalElements(),
                        reports.hasNext(),
                        size));
    }

    @Transactional
    public void deleteReport(Long reportId, CustomUserDetails userDetails) {
        Member member = requireMember(userDetails);
        ResortReport report =
                resortReportRepository
                        .findByIdWithAuthor(reportId)
                        .filter(candidate -> !candidate.isDeleted())
                        .orElseThrow(() -> new CustomException(ErrorCode.RESORT_REPORT_NOT_FOUND));
        if (!report.getAuthor().getId().equals(member.getId()) && !isAdmin(member)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        report.softDelete(LocalDateTime.now(clock.withZone(KST_ZONE)));
    }

    @Transactional
    public void updateModerationStatus(
            Long reportId, ResortReportStatus status, CustomUserDetails userDetails) {
        Member member = requireMember(userDetails);
        if (!isAdmin(member)
                || (status != ResortReportStatus.NORMAL
                        && status != ResortReportStatus.HIDDEN
                        && status != ResortReportStatus.BLOCKED)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        ResortReport report =
                resortReportRepository
                        .findByIdWithAuthor(reportId)
                        .filter(candidate -> !candidate.isDeleted())
                        .orElseThrow(() -> new CustomException(ErrorCode.RESORT_REPORT_NOT_FOUND));
        report.changeModerationStatus(status);
    }

    private void validateSeason(LocalDate date) {
        Month month = date.getMonth();
        if (month.getValue() >= Month.MAY.getValue()
                && month.getValue() <= Month.SEPTEMBER.getValue()) {
            throw new CustomException(ErrorCode.RESORT_REPORT_SEASON_CLOSED);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || page > MAX_PAGE) {
            throw new CustomException(ErrorCode.RESORT_REPORT_PAGE_LIMIT_EXCEEDED);
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new CustomException(ErrorCode.RESORT_REPORT_INVALID_PAGE_SIZE);
        }
    }

    private Member requireMember(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        return userDetails.getMember();
    }

    private boolean canDelete(ResortReport report, CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            return false;
        }
        Member member = userDetails.getMember();
        return report.getAuthor().getId().equals(member.getId()) || isAdmin(member);
    }

    private boolean isAdmin(Member member) {
        return member.getRole() == Role.ROLE_ADMIN;
    }
}
