package com.ikae.snowthing.domain.carpool.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolAutoPreviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolCreateRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolPlaceResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolSummaryResponse;
import com.ikae.snowthing.domain.carpool.service.CarpoolAutoCalculationService;
import com.ikae.snowthing.domain.carpool.service.CarpoolExternalApiRateLimiter;
import com.ikae.snowthing.domain.carpool.service.CarpoolService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/carpools", "/api/v1/carpools"})
@RequiredArgsConstructor
public class CarpoolController {

    private final CarpoolService carpoolService;
    private final CarpoolAutoCalculationService carpoolAutoCalculationService;
    private final CarpoolExternalApiRateLimiter externalApiRateLimiter;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/places")
    public ResponseEntity<List<CarpoolPlaceResponse>> searchPlaces(
            @RequestParam String query, HttpServletRequest request) {
        externalApiRateLimiter.checkPlaceSearch(clientIpResolver.resolve(request));
        return ResponseEntity.ok(carpoolAutoCalculationService.searchPlaces(query));
    }

    @PostMapping("/auto-preview")
    public ResponseEntity<CarpoolAutoPreviewResponse> previewAutomatically(
            @Valid @RequestBody CarpoolAutoPreviewRequest request, HttpServletRequest httpRequest) {
        externalApiRateLimiter.checkPreview(clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(carpoolAutoCalculationService.preview(request));
    }

    @PostMapping
    public ResponseEntity<CarpoolResponse> create(
            @Valid @RequestBody CarpoolCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        externalApiRateLimiter.checkPreview(clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(carpoolService.create(request, userDetails, clientIp));
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<CarpoolResponse> update(
            @PathVariable String publicId,
            @Valid @RequestBody CarpoolCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        externalApiRateLimiter.checkPreview(clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(carpoolService.update(publicId, request, userDetails));
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> delete(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        carpoolService.delete(publicId, userDetails);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<CarpoolSummaryResponse>> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(carpoolService.findPage(page, size));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<CarpoolResponse> find(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(carpoolService.find(publicId, userDetails));
    }
}
