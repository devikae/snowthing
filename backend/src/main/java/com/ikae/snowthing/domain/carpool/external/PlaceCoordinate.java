package com.ikae.snowthing.domain.carpool.external;

import java.math.BigDecimal;

public record PlaceCoordinate(
        String name,
        String addressName,
        String roadAddressName,
        BigDecimal longitude,
        BigDecimal latitude) {}
