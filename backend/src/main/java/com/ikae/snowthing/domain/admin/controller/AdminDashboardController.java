package com.ikae.snowthing.domain.admin.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminDashboardStatsResponse;
import com.ikae.snowthing.domain.admin.service.VisitorStatsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final VisitorStatsService visitorStatsService;

    @GetMapping("/stats")
    public ResponseEntity<AdminDashboardStatsResponse> getDashboardStats(
            @RequestParam(name = "days", defaultValue = "7") int days) {
        AdminDashboardStatsResponse response = visitorStatsService.getDashboardStats();
        return ResponseEntity.ok(response);
    }
}
