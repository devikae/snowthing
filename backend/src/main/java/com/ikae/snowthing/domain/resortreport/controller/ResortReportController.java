package com.ikae.snowthing.domain.resortreport.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.resortreport.dto.ResortReportCreateRequest;
import com.ikae.snowthing.domain.resortreport.dto.ResortReportResponse;
import com.ikae.snowthing.domain.resortreport.service.ResortReportService;
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
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        ResortReportResponse response =
                resortReportService.createReport(userDetails.getMember().getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/today")
    public ResponseEntity<List<ResortReportResponse>> getTodayReports(
            @RequestParam(required = false) Long resortId,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        List<ResortReportResponse> responses =
                resortReportService.getTodayReports(resortId, limit);
        return ResponseEntity.ok(responses);
    }
}
