package com.ikae.snowthing.domain.resortreport.controller;

import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.AdminPage;
import com.ikae.snowthing.domain.admin.dto.AdminResortReportItem;
import com.ikae.snowthing.domain.admin.service.AdminQueryService;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportModerationStatusUpdateRequest;
import com.ikae.snowthing.domain.resortreport.service.ResortReportService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/resort-reports")
@RequiredArgsConstructor
public class ResortReportAdminController {

    private final ResortReportService resortReportService;
    private final AdminQueryService adminQueryService;

    /** 관리자 설질 제보 목록 (IKA-78). status는 ALL, NORMAL, HIDDEN, BLOCKED, DELETED. */
    @GetMapping
    public ResponseEntity<AdminPage<AdminResortReportItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(adminQueryService.resortReports(userDetails, status, page, size));
    }

    @PatchMapping("/{reportId}/moderation-status")
    public ResponseEntity<Void> updateModerationStatus(
            @PathVariable Long reportId,
            @Valid @RequestBody ResortReportModerationStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        resortReportService.updateModerationStatus(
                reportId, request.moderationStatus(), userDetails);
        return ResponseEntity.noContent().build();
    }
}
