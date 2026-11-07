package com.ikae.snowthing.domain.contentreport.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.contentreport.dto.ContentReportCreateRequest;
import com.ikae.snowthing.domain.contentreport.service.ContentReportService;
import com.ikae.snowthing.global.security.ContentWriteRateLimiter;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ContentReportController {

    private final ContentReportService reportService;
    private final ContentWriteRateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    public ResponseEntity<Void> create(
            @Valid @RequestBody ContentReportCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails user,
            HttpServletRequest httpRequest) {
        rateLimiter.check(
                user,
                clientIpResolver.resolve(httpRequest),
                ContentWriteRateLimiter.Action.CONTENT_REPORT);
        reportService.create(request, user);
        return ResponseEntity.noContent().build();
    }
}
