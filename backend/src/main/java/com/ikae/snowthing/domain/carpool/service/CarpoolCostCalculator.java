package com.ikae.snowthing.domain.carpool.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;

public final class CarpoolCostCalculator {

    private static final int DRIVER_COUNT = 1;
    private static final int CURRENCY_SCALE = 0;
    private static final RoundingMode COST_ROUNDING = RoundingMode.CEILING;

    private CarpoolCostCalculator() {}

    public static Calculation calculate(
            BigDecimal distanceKm,
            BigDecimal fuelEfficiency,
            BigDecimal fuelPrice,
            int tollFee,
            int passengerCapacity) {
        validatePositive(distanceKm);
        validatePositive(fuelEfficiency);
        validateNonNegative(fuelPrice);
        validateNonNegative(tollFee);
        if (passengerCapacity <= 0) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_VALUE);
        }

        BigDecimal fuelCost =
                distanceKm
                        .divide(fuelEfficiency, CURRENCY_SCALE + 4, COST_ROUNDING)
                        .multiply(fuelPrice)
                        .setScale(CURRENCY_SCALE, COST_ROUNDING);
        int estimatedFuelCost = fuelCost.intValueExact();
        int estimatedTotalCost = Math.addExact(estimatedFuelCost, tollFee);
        int totalPassengerCount = Math.addExact(DRIVER_COUNT, passengerCapacity);
        int estimatedCostPerPerson =
                BigDecimal.valueOf(estimatedTotalCost)
                        .divide(
                                BigDecimal.valueOf(totalPassengerCount),
                                CURRENCY_SCALE,
                                COST_ROUNDING)
                        .intValueExact();

        return new Calculation(
                estimatedFuelCost, estimatedTotalCost, estimatedCostPerPerson, totalPassengerCount);
    }

    private static void validatePositive(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
    }

    private static void validateNonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
    }

    private static void validateNonNegative(int value) {
        if (value < 0) {
            throw new CustomAuthException(ErrorCode.INVALID_CARPOOL_VALUE);
        }
    }

    public record Calculation(
            int estimatedFuelCost,
            int estimatedTotalCost,
            int estimatedCostPerPerson,
            int totalPassengerCount) {}
}
