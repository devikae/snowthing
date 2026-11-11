package com.ikae.snowthing.domain.admin.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminDashboardStatsResponse;
import com.ikae.snowthing.domain.admin.dto.AdminOverviewResponse;
import com.ikae.snowthing.domain.admin.dto.AdminOverviewResponse.DailyCount;
import com.ikae.snowthing.domain.admin.repository.AdminQueryRepository;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final SystemConfigService systemConfigService;
    private final AdminQueryRepository adminQueryRepository;

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getDashboardStats() {
        LocalDate today = LocalDate.now(ZONE_KST);

        return new AdminDashboardStatsResponse(
                memberRepository.count(),
                memberRepository.countByCreatedAtAfter(today.atStartOfDay()),
                systemConfigService.isAnonymousChatEnabled());
    }

    /**
     * 최근 days일(오늘 포함) 일별 가입, 글, 댓글 수와 처리 대기 건수 (IKA-78). 날짜 경계는 KST 자정이다. created_at은 KST 시각으로
     * 저장된다. 글과 댓글은 지금 삭제된 것도 그날 작성된 수에 넣는다.
     */
    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview(CustomUserDetails admin, Integer days) {
        AdminAccess.require(admin);
        int range = days == null ? 7 : days;
        if (range != 7 && range != 30) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        LocalDate today = LocalDate.now(ZONE_KST);
        LocalDate first = today.minusDays(range - 1L);
        LocalDateTime from = first.atStartOfDay();
        Map<LocalDate, Long> signups = adminQueryRepository.countDaily("member", from);
        Map<LocalDate, Long> posts = adminQueryRepository.countDaily("post", from);
        Map<LocalDate, Long> comments = adminQueryRepository.countDaily("comment", from);
        List<DailyCount> series = new ArrayList<>(range);
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            series.add(
                    new DailyCount(
                            day,
                            signups.getOrDefault(day, 0L),
                            posts.getOrDefault(day, 0L),
                            comments.getOrDefault(day, 0L)));
        }
        return new AdminOverviewResponse(
                range,
                series.get(series.size() - 1),
                List.copyOf(series),
                adminQueryRepository.countPendingReports(),
                adminQueryRepository.countUnansweredInquiries());
    }
}
