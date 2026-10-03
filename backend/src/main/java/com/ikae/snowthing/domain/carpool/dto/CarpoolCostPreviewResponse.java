package com.ikae.snowthing.domain.carpool.dto;

import com.ikae.snowthing.domain.carpool.service.CarpoolCostCalculator;

public record CarpoolCostPreviewResponse(
        int estimatedFuelCost,
        int estimatedTotalCost,
        int estimatedCostPerPerson,
        int totalPassengerCount) {

    public static CarpoolCostPreviewResponse from(CarpoolCostCalculator.Calculation calculation) {
        return new CarpoolCostPreviewResponse(
                calculation.estimatedFuelCost(),
                calculation.estimatedTotalCost(),
                calculation.estimatedCostPerPerson(),
                calculation.totalPassengerCount());
    }
}
