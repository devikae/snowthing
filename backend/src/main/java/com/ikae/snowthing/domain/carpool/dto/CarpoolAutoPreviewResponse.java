package com.ikae.snowthing.domain.carpool.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelPriceSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;

public record CarpoolAutoPreviewResponse(
        BigDecimal distanceKm,
        int tollFee,
        int durationSeconds,
        BigDecimal fuelPrice,
        LocalDate fuelPriceTradeDate,
        LocalDateTime fuelPriceObservedAt,
        CarpoolFuelPriceSource fuelPriceSource,
        int estimatedFuelCost,
        int estimatedTotalCost,
        int estimatedCostPerPerson,
        int totalPassengerCount,
        CarpoolRouteSource routeSource,
        LocalDateTime routeCalculatedAt) {

    public CarpoolAutoPreviewResponse(
            BigDecimal distanceKm,
            int tollFee,
            int durationSeconds,
            BigDecimal fuelPrice,
            LocalDate fuelPriceTradeDate,
            LocalDateTime fuelPriceObservedAt,
            int estimatedFuelCost,
            int estimatedTotalCost,
            int estimatedCostPerPerson,
            int totalPassengerCount,
            CarpoolRouteSource routeSource,
            LocalDateTime routeCalculatedAt) {
        this(
                distanceKm,
                tollFee,
                durationSeconds,
                fuelPrice,
                fuelPriceTradeDate,
                fuelPriceObservedAt,
                CarpoolFuelPriceSource.OPINET,
                estimatedFuelCost,
                estimatedTotalCost,
                estimatedCostPerPerson,
                totalPassengerCount,
                routeSource,
                routeCalculatedAt);
    }
}
