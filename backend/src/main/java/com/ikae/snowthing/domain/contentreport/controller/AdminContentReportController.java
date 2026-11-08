package com.ikae.snowthing.domain.contentreport.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.contentreport.dto.AdminContentReportPageResponse;
import com.ikae.snowthing.domain.contentreport.dto.ModerationActionRequest;
import com.ikae.snowthing.domain.contentreport.entity.ReportStatus;
import com.ikae.snowthing.domain.contentreport.service.ContentModerationService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminContentReportController {

    private final ContentModerationService moderationService;

    @GetMapping
    public ResponseEntity<AdminContentReportPageResponse> list(
            @RequestParam(defaultValue = "PENDING") ReportStatus status,
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(moderationService.list(status, page));
    }

    @PostMapping("/{reportId}/actions")
    public ResponseEntity<Void> apply(
            @PathVariable Long reportId,
            @Valid @RequestBody ModerationActionRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        moderationService.apply(reportId, request, admin.getUsername());
        return ResponseEntity.noContent().build();
    }
}
