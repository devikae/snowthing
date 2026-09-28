package com.ikae.snowthing.domain.market.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.market.dto.MarketModerationStatusUpdateRequest;
import com.ikae.snowthing.domain.market.service.MarketListingService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/market-listings")
@RequiredArgsConstructor
public class MarketAdminController {

    private final MarketListingService marketListingService;

    @PatchMapping("/{publicId}/moderation-status")
    public ResponseEntity<Void> updateModerationStatus(
            @PathVariable String publicId,
            @Valid @RequestBody MarketModerationStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        marketListingService.updateModerationStatus(
                publicId, request.moderationStatus(), userDetails);
        return ResponseEntity.noContent().build();
    }
}
