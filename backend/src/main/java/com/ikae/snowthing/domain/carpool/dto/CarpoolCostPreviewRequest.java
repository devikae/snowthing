package com.ikae.snowthing.domain.carpool.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CarpoolCostPreviewRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal distanceKm,
        @NotNull @DecimalMin(value = "0.01") BigDecimal fuelEfficiency,
        @NotNull @DecimalMin(value = "0") BigDecimal fuelPrice,
        @Min(0) int tollFee,
        @Min(1) int passengerCapacity) {}
