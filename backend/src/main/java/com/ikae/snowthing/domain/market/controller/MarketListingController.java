package com.ikae.snowthing.domain.market.controller;

import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.market.dto.*;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TradeStatus;
import com.ikae.snowthing.domain.market.service.MarketListingService;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;
import com.ikae.snowthing.global.web.ViewCountCookieManager;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/market-listings")
@RequiredArgsConstructor
public class MarketListingController {

    private static final String DEFAULT_PAGE = "1";
    private static final String DEFAULT_PAGE_SIZE = "20";

    private final MarketListingService marketListingService;
    private final ClientIpResolver clientIpResolver;
    private final ViewCountCookieManager viewCountCookieManager;

    @GetMapping("/preview")
    public ResponseEntity<MarketPreviewResponse> getPreview() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(marketListingService.getPreview());
    }

    @GetMapping
    public ResponseEntity<CursorPageResponse<MarketListingSummaryResponse>> search(
            @RequestParam(defaultValue = DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) ProductCondition productCondition,
            @RequestParam(required = false) TradeStatus tradeStatus) {
        return ResponseEntity.ok(
                marketListingService.search(
                        page, size, keyword, categoryCode, productCondition, tradeStatus));
    }

    @PostMapping
    public ResponseEntity<MarketListingCreateResponse> create(
            @Valid @RequestBody MarketListingCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        marketListingService.create(
                                request, userDetails, clientIpResolver.resolve(httpRequest)));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<MarketListingDetailResponse> getDetail(
            @PathVariable String publicId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest request,
            HttpServletResponse response) {
        boolean increaseViewCount =
                viewCountCookieManager.markIfFirstView(publicId, request, response);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(marketListingService.getDetail(publicId, userDetails, increaseViewCount));
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<MarketListingUpdateResponse> update(
            @PathVariable String publicId,
            @Valid @RequestBody MarketListingUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(marketListingService.update(publicId, request, userDetails));
    }

    @PatchMapping("/{publicId}/trade-status")
    public ResponseEntity<MarketTradeStatusUpdateResponse> updateTradeStatus(
            @PathVariable String publicId,
            @Valid @RequestBody MarketTradeStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                marketListingService.updateTradeStatus(publicId, request, userDetails));
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> delete(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        marketListingService.delete(publicId, userDetails);
        return ResponseEntity.noContent().build();
    }
}
