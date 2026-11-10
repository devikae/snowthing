package com.ikae.snowthing.domain.resortreport.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.resortreport.dto.ResortReportCreateRequest;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportResponse;
import com.ikae.snowthing.domain.resortreport.service.ResortReportService;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.idempotency.IdempotencyTarget;
import com.ikae.snowthing.global.idempotency.IdempotentCreateHandler;
import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/resort-reports")
@RequiredArgsConstructor
public class ResortReportController {

    private final ResortReportService resortReportService;
    private final ContentWriteRateLimiter contentWriteRateLimiter;
    private final IdempotentCreateHandler idempotentCreateHandler;

    @PostMapping
    public ResponseEntity<Object> createReport(
            @Valid @RequestBody ResortReportCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader(value = IdempotentCreateHandler.HEADER, required = false)
                    String idempotencyKey) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new CustomException(ErrorCode.LOGIN_REQUIRED);
        }
        return idempotentCreateHandler.create(
                IdempotencyTarget.RESORT_REPORT,
                idempotencyKey,
                userDetails,
                null,
                request,
                () ->
                        contentWriteRateLimiter.check(
                                userDetails, null, ContentWriteRateLimiter.Action.RESORT_REPORT),
                () -> resortReportService.createReport(userDetails.getMember().getId(), request));
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
