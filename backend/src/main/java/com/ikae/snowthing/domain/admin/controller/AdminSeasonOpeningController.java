package com.ikae.snowthing.domain.admin.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.admin.dto.SeasonOpeningResponse;
import com.ikae.snowthing.domain.admin.dto.SeasonOpeningUpdateRequest;
import com.ikae.snowthing.domain.admin.service.SeasonOpeningService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 리조트 개장일 관리 (IKA-87). 권한은 SecurityConfig가 /api/v1/admin/ 아래 전체를 ROLE_ADMIN으로 막는다. */
@RestController
@RequestMapping("/api/v1/admin/season-openings")
@RequiredArgsConstructor
public class AdminSeasonOpeningController {

    private final SeasonOpeningService seasonOpeningService;

    @GetMapping
    public ResponseEntity<SeasonOpeningResponse> getSeasonOpenings() {
        return ResponseEntity.ok(seasonOpeningService.getSeasonOpenings());
    }

    @PutMapping
    public ResponseEntity<SeasonOpeningResponse> updateSeasonOpenings(
            @Valid @RequestBody SeasonOpeningUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String updatedBy =
                (userDetails != null && userDetails.getUsername() != null)
                        ? userDetails.getUsername()
                        : "ADMIN";
        return ResponseEntity.ok(seasonOpeningService.update(request, updatedBy));
    }
}
