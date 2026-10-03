package com.ikae.snowthing.domain.carpool.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.carpool.dto.CarpoolCostPreviewRequest;
import com.ikae.snowthing.domain.carpool.dto.CarpoolCostPreviewResponse;
import com.ikae.snowthing.domain.carpool.service.CarpoolCostCalculator;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping({"/api/carpools", "/api/v1/carpools"})
@RequiredArgsConstructor
public class CarpoolController {

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
}
