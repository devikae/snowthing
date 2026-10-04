package com.ikae.snowthing.domain.carpool.dto;

import java.math.BigDecimal;

import com.ikae.snowthing.domain.carpool.external.PlaceCoordinate;

public record CarpoolPlaceResponse(
        String name,
        String addressName,
        String roadAddressName,
        BigDecimal longitude,
        BigDecimal latitude) {

    public static CarpoolPlaceResponse from(PlaceCoordinate place) {
        return new CarpoolPlaceResponse(
                place.name(),
                place.addressName(),
                place.roadAddressName(),
                place.longitude(),
                place.latitude());
    }
}
