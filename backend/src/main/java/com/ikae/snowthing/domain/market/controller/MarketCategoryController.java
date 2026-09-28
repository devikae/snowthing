package com.ikae.snowthing.domain.market.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.market.dto.MarketCategoryListResponse;
import com.ikae.snowthing.domain.market.service.MarketListingService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/market/categories")
@RequiredArgsConstructor
public class MarketCategoryController {

    private final MarketListingService marketListingService;

    @GetMapping
    public ResponseEntity<MarketCategoryListResponse> getCategories() {
        return ResponseEntity.ok(marketListingService.getCategories());
    }
}
