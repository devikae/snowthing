package com.ikae.snowthing.domain.carpool.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ikae.snowthing.domain.carpool.entity.*;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.post.entity.Post;

public record CarpoolResponse(
        String publicId,
        String title,
        String content,
        String writerName,
        String departureRegion,
        String meetingPlace,
        Long destinationResortId,
        String destinationResortName,
        CarpoolTripType tripType,
        LocalDateTime departureAt,
        LocalDateTime returnAt,
        int passengerCapacity,
        CarpoolFuelType fuelType,
        BigDecimal fuelEfficiency,
        BigDecimal fuelPrice,
        LocalDateTime fuelPriceObservedAt,
        BigDecimal routeDistanceKm,
        int routeTollFee,
        int estimatedFuelCost,
        int estimatedTotalCost,
        int estimatedCostPerPerson,
        CarpoolRouteSource routeSource,
        LocalDateTime routeCalculatedAt,
        String contactInfo,
        boolean contactPublicToGuest,
        LocalDateTime createdAt) {

    public static CarpoolResponse from(Post post, CarpoolDetail detail, String contactInfo) {
        String writerName = post.getMember() == null ? "익명보더" : post.getMember().getNickname();
        return new CarpoolResponse(
                post.getPublicId(),
                post.getTitle(),
                post.getContent(),
                writerName,
                detail.getDepartureRegion(),
                detail.getMeetingPlace(),
                detail.getDestinationResort().getId(),
                detail.getDestinationResort().getName(),
                detail.getTripType(),
                detail.getDepartureAt(),
                detail.getReturnAt(),
                detail.getPassengerCapacity(),
                detail.getFuelType(),
                detail.getFuelEfficiency(),
                detail.getFuelPrice(),
                detail.getFuelPriceObservedAt(),
                detail.getRouteDistanceKm(),
                detail.getRouteTollFee(),
                detail.getEstimatedFuelCost(),
                detail.getEstimatedTotalCost(),
                detail.getEstimatedCostPerPerson(),
                detail.getRouteSource(),
                detail.getRouteCalculatedAt(),
                contactInfo,
                detail.isContactPublicToGuest(),
                post.getCreatedAt());
    }
}
