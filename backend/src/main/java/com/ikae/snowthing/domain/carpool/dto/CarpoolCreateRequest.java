package com.ikae.snowthing.domain.carpool.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.*;

import com.ikae.snowthing.domain.carpool.CarpoolLimits;
import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;

public record CarpoolCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = CarpoolLimits.MAX_CONTENT_LENGTH) String content,
        @NotBlank @Size(max = 100) String departureRegion,
        @NotBlank @Size(max = 200) String meetingPlace,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal departureLatitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal departureLongitude,
        @NotNull Long destinationResortId,
        @NotNull CarpoolTripType tripType,
        @NotNull @Future LocalDateTime departureAt,
        LocalDateTime returnAt,
        @Min(1) @Max(CarpoolLimits.MAX_PASSENGER_CAPACITY) int passengerCapacity,
        @NotNull CarpoolFuelType fuelType,
        @NotNull
                @DecimalMin("0.01")
                @DecimalMax(CarpoolLimits.MAX_FUEL_EFFICIENCY)
                @Digits(integer = 3, fraction = 2)
                BigDecimal fuelEfficiency,
        @NotNull CarpoolCostMode costMode,
        @DecimalMin("1")
                @DecimalMax(CarpoolLimits.MAX_MANUAL_COST_PER_PERSON)
                @Digits(integer = 7, fraction = 0)
                BigDecimal manualCostPerPerson,
        boolean equipmentLoadAvailable,
        @Size(max = 500) String contactInfo,
        boolean contactPublicToGuest,
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

    public CarpoolCreateRequest(
            String title,
            String content,
            String departureRegion,
            String meetingPlace,
            BigDecimal departureLatitude,
            BigDecimal departureLongitude,
            Long destinationResortId,
            CarpoolTripType tripType,
            LocalDateTime departureAt,
            LocalDateTime returnAt,
            int passengerCapacity,
            CarpoolFuelType fuelType,
            BigDecimal fuelEfficiency,
            CarpoolCostMode costMode,
            BigDecimal manualCostPerPerson,
            boolean equipmentLoadAvailable,
            String contactInfo,
            boolean contactPublicToGuest) {
        this(
                title,
                content,
                departureRegion,
                meetingPlace,
                departureLatitude,
                departureLongitude,
                destinationResortId,
                tripType,
                departureAt,
                returnAt,
                passengerCapacity,
                fuelType,
                fuelEfficiency,
                costMode,
                manualCostPerPerson,
                equipmentLoadAvailable,
                contactInfo,
                contactPublicToGuest,
                CarpoolRouteSource.KAKAO,
                null,
                null,
                null);
    }
}
