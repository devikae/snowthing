package com.ikae.snowthing.domain.carpool.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.*;

import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelType;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRouteSource;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;

public record CarpoolCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        @NotBlank @Size(max = 100) String departureRegion,
        @NotBlank @Size(max = 200) String meetingPlace,
        BigDecimal departureLatitude,
        BigDecimal departureLongitude,
        @NotNull Long destinationResortId,
        @NotNull CarpoolTripType tripType,
        @NotNull @Future LocalDateTime departureAt,
        LocalDateTime returnAt,
        @Min(1) int passengerCapacity,
        @NotNull CarpoolFuelType fuelType,
        @NotNull @DecimalMin("0.01") BigDecimal fuelEfficiency,
        @NotNull @DecimalMin("0") BigDecimal fuelPrice,
        @NotNull @DecimalMin("0.01") BigDecimal routeDistanceKm,
        @Min(0) int routeTollFee,
        @NotNull CarpoolRouteSource routeSource,
        @Size(max = 500) String contactInfo,
        boolean contactPublicToGuest) {}
