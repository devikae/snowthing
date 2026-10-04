package com.ikae.snowthing.domain.carpool.external;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RouteCalculation(
        BigDecimal distanceKm, int tollFee, int durationSeconds, LocalDateTime calculatedAt) {}
