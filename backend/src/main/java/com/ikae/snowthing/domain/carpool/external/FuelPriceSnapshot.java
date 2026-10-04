package com.ikae.snowthing.domain.carpool.external;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ikae.snowthing.domain.carpool.entity.CarpoolFuelPriceSource;

public record FuelPriceSnapshot(
        String productCode,
        BigDecimal pricePerLiter,
        LocalDate tradeDate,
        LocalDateTime observedAt,
        CarpoolFuelPriceSource source) {

    public FuelPriceSnapshot(
            String productCode,
            BigDecimal pricePerLiter,
            LocalDate tradeDate,
            LocalDateTime observedAt) {
        this(productCode, pricePerLiter, tradeDate, observedAt, CarpoolFuelPriceSource.OPINET);
    }

    public FuelPriceSnapshot withSource(CarpoolFuelPriceSource nextSource) {
        return new FuelPriceSnapshot(productCode, pricePerLiter, tradeDate, observedAt, nextSource);
    }
}
