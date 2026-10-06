package com.ikae.snowthing.domain.resortreport.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.resortreport.dto.ResortReportCreateRequest;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportResponse;
import com.ikae.snowthing.domain.resortreport.service.ResortReportService;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/resort-reports")
@RequiredArgsConstructor
public class ResortReportController {

    private final ResortReportService resortReportService;

    @PostMapping
    public ResponseEntity<ResortReportResponse> createReport(
            @Valid @RequestBody ResortReportCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        ResortReportResponse response =
                resortReportService.createReport(userDetails.getMember().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/today")
    public ResponseEntity<CursorPageResponse<ResortReportResponse>> getTodayReports(
            @RequestParam(required = false) Long resortId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CursorPageResponse<ResortReportResponse> responses =
                resortReportService.getTodayReports(resortId, page, size, userDetails);
        return ResponseEntity.ok(responses);
    }

    @DeleteMapping("/{reportId}")
    public ResponseEntity<Void> deleteReport(
            @PathVariable Long reportId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        resortReportService.deleteReport(reportId, userDetails);
        return ResponseEntity.noContent().build();
    }
}
