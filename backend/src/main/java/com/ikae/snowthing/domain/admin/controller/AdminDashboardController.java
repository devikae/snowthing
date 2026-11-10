package com.ikae.snowthing.domain.admin.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminDashboardStatsResponse;
import com.ikae.snowthing.domain.admin.dto.AdminOverviewResponse;
import com.ikae.snowthing.domain.admin.service.AdminDashboardService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/stats")
    public ResponseEntity<AdminDashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(adminDashboardService.getDashboardStats());
    }

    /** 최근 7일이나 30일 일별 추이와 처리 대기 건수 (IKA-78). */
    @GetMapping("/overview")
    public ResponseEntity<AdminOverviewResponse> getOverview(
            @RequestParam(required = false) Integer days,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminDashboardService.getOverview(admin, days));
    }
}
