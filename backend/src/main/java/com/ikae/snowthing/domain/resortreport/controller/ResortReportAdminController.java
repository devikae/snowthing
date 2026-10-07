package com.ikae.snowthing.domain.resortreport.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.resortreport.dto.ResortReportModerationStatusUpdateRequest;
import com.ikae.snowthing.domain.resortreport.service.ResortReportService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/resort-reports")
@RequiredArgsConstructor
public class ResortReportAdminController {

    private final ResortReportService resortReportService;

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
