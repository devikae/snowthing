package com.ikae.snowthing.domain.carpool.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.ikae.snowthing.domain.carpool.CarpoolLimits;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;

public record CarpoolAutoPreviewRequest(
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal originLongitude,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal originLatitude,
        @NotNull Long destinationResortId,
        @NotNull CarpoolTripType tripType,
        @NotNull CarpoolFuelType fuelType,
        @NotNull
                @DecimalMin("0.01")
                @DecimalMax(CarpoolLimits.MAX_FUEL_EFFICIENCY)
                @Digits(integer = 3, fraction = 2)
                BigDecimal fuelEfficiency,
        @Min(1) @Max(CarpoolLimits.MAX_PASSENGER_CAPACITY) int passengerCapacity,
        @NotNull CarpoolRouteSource routeSource,
        @DecimalMin(CarpoolLimits.MIN_MANUAL_DISTANCE_KM)
                @DecimalMax(CarpoolLimits.MAX_MANUAL_DISTANCE_KM)
                @Digits(integer = 4, fraction = 2)
                BigDecimal manualDistanceKm,
        @Min(CarpoolLimits.MIN_MANUAL_TOLL_FEE) @Max(CarpoolLimits.MAX_MANUAL_TOLL_FEE)
                Integer manualTollFee,
        @DecimalMin(CarpoolLimits.MIN_MANUAL_FUEL_PRICE)
                @DecimalMax(CarpoolLimits.MAX_MANUAL_FUEL_PRICE)
                @Digits(integer = 4, fraction = 2)
                BigDecimal manualFuelPrice) {

    public CarpoolAutoPreviewRequest(
            BigDecimal originLongitude,
            BigDecimal originLatitude,
            Long destinationResortId,
            CarpoolTripType tripType,
            CarpoolFuelType fuelType,
            BigDecimal fuelEfficiency,
            int passengerCapacity) {
        this(
                originLongitude,
                originLatitude,
                destinationResortId,
                tripType,
                fuelType,
                fuelEfficiency,
                passengerCapacity,
                CarpoolRouteSource.KAKAO,
                null,
                null,
                null);
    }
}
