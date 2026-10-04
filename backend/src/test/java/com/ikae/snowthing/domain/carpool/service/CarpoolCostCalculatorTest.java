package com.ikae.snowthing.domain.carpool.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class CarpoolCostCalculatorTest {

    private static final BigDecimal DISTANCE_KM = BigDecimal.valueOf(200);
    private static final BigDecimal FUEL_EFFICIENCY = BigDecimal.valueOf(10);
    private static final BigDecimal FUEL_PRICE = BigDecimal.valueOf(1858);
    private static final int TOLL_FEE = 10000;
    private static final int PASSENGER_CAPACITY = 3;

    @Test
    void calculatesFuelTotalAndDriverIncludedShare() {
        CarpoolCostCalculator.Calculation result =
                CarpoolCostCalculator.calculate(
                        DISTANCE_KM, FUEL_EFFICIENCY, FUEL_PRICE, TOLL_FEE, PASSENGER_CAPACITY);

        assertThat(result.estimatedFuelCost()).isEqualTo(37160);
        assertThat(result.estimatedTotalCost()).isEqualTo(47160);
        assertThat(result.estimatedCostPerPerson()).isEqualTo(11790);
        assertThat(result.totalPassengerCount()).isEqualTo(4);
    }
}
