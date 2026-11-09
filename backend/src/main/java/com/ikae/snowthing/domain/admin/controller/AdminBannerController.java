package com.ikae.snowthing.domain.admin.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.BannerCreateRequest;
import com.ikae.snowthing.domain.admin.dto.BannerResponse;
import com.ikae.snowthing.domain.admin.dto.BannerUpdateRequest;
import com.ikae.snowthing.domain.admin.service.BannerService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;

    @GetMapping
    public ResponseEntity<List<BannerResponse>> getAllBanners() {
        return ResponseEntity.ok(bannerService.getBanners(false));
    }

    @PostMapping
    public ResponseEntity<BannerResponse> createBanner(
            @Valid @RequestBody BannerCreateRequest request) {
        return ResponseEntity.ok(bannerService.createBanner(request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BannerResponse> updateBanner(
            @PathVariable("id") Long id, @Valid @RequestBody BannerUpdateRequest request) {
        return ResponseEntity.ok(bannerService.updateBanner(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBanner(@PathVariable("id") Long id) {
        bannerService.deleteBanner(id);
        return ResponseEntity.noContent().build();
    }
}
