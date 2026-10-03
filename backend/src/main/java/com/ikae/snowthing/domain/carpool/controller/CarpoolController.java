package com.ikae.snowthing.domain.carpool.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.carpool.dto.CarpoolCostPreviewRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolCostPreviewResponse;
import com.ikae.snowthing.domain.carpool.dto.CarpoolCreateRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolResponse;
import com.ikae.snowthing.domain.carpool.service.CarpoolCostCalculator;
import com.ikae.snowthing.domain.carpool.service.CarpoolService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/carpools", "/api/v1/carpools"})
@RequiredArgsConstructor
public class CarpoolController {

    private final CarpoolService carpoolService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/cost-preview")
    public ResponseEntity<CarpoolCostPreviewResponse> previewCost(
            @Valid @RequestBody CarpoolCostPreviewRequest request) {
        CarpoolCostCalculator.Calculation calculation =
                CarpoolCostCalculator.calculate(
                        request.distanceKm(),
                        request.fuelEfficiency(),
                        request.fuelPrice(),
                        request.tollFee(),
                        request.passengerCapacity());
        return ResponseEntity.ok(CarpoolCostPreviewResponse.from(calculation));
    }

    @PostMapping
    public ResponseEntity<CarpoolResponse> create(
            @Valid @RequestBody CarpoolCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(201)
                .body(
                        carpoolService.create(
                                request, userDetails, clientIpResolver.resolve(httpRequest)));
    }

    @GetMapping
    public ResponseEntity<Page<CarpoolResponse>> findPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(carpoolService.findPage(page, size, userDetails));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<CarpoolResponse> find(
            @PathVariable String publicId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(carpoolService.find(publicId, userDetails));
    }
}
