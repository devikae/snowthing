package com.ikae.snowthing.domain.admin.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminDashboardStatsResponse;
import com.ikae.snowthing.domain.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final ZoneId ZONE_KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final SystemConfigService systemConfigService;

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse getDashboardStats() {
        LocalDate today = LocalDate.now(ZONE_KST);

        return new AdminDashboardStatsResponse(
                memberRepository.count(),
                memberRepository.countByCreatedAtAfter(today.atStartOfDay()),
                systemConfigService.isAnonymousChatEnabled());
    }
}
