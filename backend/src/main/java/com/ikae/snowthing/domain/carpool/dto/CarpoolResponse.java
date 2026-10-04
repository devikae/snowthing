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
        BigDecimal departureLatitude,
        BigDecimal departureLongitude,
        Long destinationResortId,
        String destinationResortName,
        CarpoolTripType tripType,
        LocalDateTime departureAt,
        LocalDateTime returnAt,
        int passengerCapacity,
        CarpoolFuelType fuelType,
        BigDecimal fuelEfficiency,
        CarpoolCostMode costMode,
        BigDecimal fuelPrice,
        CarpoolFuelPriceSource fuelPriceSource,
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
        boolean equipmentLoadAvailable,
        boolean canManage,
        LocalDateTime createdAt) {

    public static CarpoolResponse from(
            Post post, CarpoolDetail detail, String contactInfo, boolean canManage) {
        String writerName = post.getMember() == null ? "익명보더" : post.getMember().getNickname();
        return new CarpoolResponse(
                post.getPublicId(),
                post.getTitle(),
                post.getContent(),
                writerName,
                detail.getDepartureRegion(),
                detail.getMeetingPlace(),
                canManage ? detail.getDepartureLatitude() : null,
                canManage ? detail.getDepartureLongitude() : null,
                detail.getDestinationResort().getId(),
                detail.getDestinationResort().getName(),
                detail.getTripType(),
                detail.getDepartureAt(),
                detail.getReturnAt(),
                detail.getPassengerCapacity(),
                detail.getFuelType(),
                detail.getFuelEfficiency(),
                detail.getCostMode(),
                detail.getFuelPrice(),
                detail.getFuelPriceSource(),
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
                detail.isEquipmentLoadAvailable(),
                canManage,
                post.getCreatedAt());
    }
}
