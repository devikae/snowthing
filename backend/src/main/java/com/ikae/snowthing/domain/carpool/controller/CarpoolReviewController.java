package com.ikae.snowthing.domain.carpool.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.carpool.dto.CarpoolMyReviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolParticipationResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolReviewCreateRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolReviewSummaryResponse;
import com.ikae.snowthing.domain.carpool.service.CarpoolReviewService;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 카풀 탑승 확인과 후기. GET은 SecurityConfig의 카풀 조회 허용을 따르고, 쓰기는 로그인이 필요하다.
 *
 * <p>설계: docs/conception/027_carpool_review/01_design.md
 */
@RestController
@RequestMapping("/api/v1/carpools")
@RequiredArgsConstructor
public class CarpoolReviewController {

    private final CarpoolReviewService carpoolReviewService;

    @GetMapping("/{publicId}/participation")
    public ResponseEntity<CarpoolParticipationResponse> participation(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(carpoolReviewService.participation(publicId, userDetails));
    }

    @PostMapping("/{publicId}/riders")
    public ResponseEntity<Void> requestRide(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        carpoolReviewService.requestRide(publicId, userDetails);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/riders/{memberPublicId}/confirm")
    public ResponseEntity<Void> confirmRide(
            @PathVariable String publicId,
            @PathVariable String memberPublicId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        carpoolReviewService.decideRide(publicId, memberPublicId, true, userDetails);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/riders/{memberPublicId}/reject")
    public ResponseEntity<Void> rejectRide(
            @PathVariable String publicId,
            @PathVariable String memberPublicId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        carpoolReviewService.decideRide(publicId, memberPublicId, false, userDetails);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/reviews")
    public ResponseEntity<Void> writeReview(
            @PathVariable String publicId,
            @Valid @RequestBody CarpoolReviewCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        carpoolReviewService.writeReview(publicId, request, userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/members/{memberPublicId}/review-summary")
    public ResponseEntity<CarpoolReviewSummaryResponse> summary(
            @PathVariable String memberPublicId) {
        return ResponseEntity.ok(carpoolReviewService.summary(memberPublicId));
    }

    @GetMapping("/reviews/me")
    public ResponseEntity<CarpoolMyReviewResponse> mine(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(carpoolReviewService.mine(userDetails));
    }
}
