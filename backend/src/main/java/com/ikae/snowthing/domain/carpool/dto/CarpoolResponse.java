package com.ikae.snowthing.domain.carpool.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.ikae.snowthing.domain.carpool.entity.*;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;

public record CarpoolResponse(
        String publicId,
        String title,
        String content,
        String writerPublicId,
        String writerName,
        String writerProfileImageUrl,
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
        Integer routeTollFee,
        Integer estimatedFuelCost,
        Integer estimatedTotalCost,
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
        Member writer = post.getMember();
        boolean hidden = writer == null || writer.isWithdrawn();
        String writerName =
                writer == null
                        ? "익명보더"
                        : writer.isWithdrawn() ? Member.WITHDRAWN_NICKNAME : writer.getNickname();
        // 예전 직접 입력 글에는 계산 값이 남아 있지만 새 글과 똑같이 값 없이 내려준다.
        boolean auto = detail.getCostMode() == CarpoolCostMode.AUTO;
        return new CarpoolResponse(
                post.getPublicId(),
                post.getTitle(),
                post.getContent(),
                hidden ? null : writer.getPublicId(),
                writerName,
                hidden ? null : writer.getProfileImageUrl(),
                detail.getDepartureRegion(),
                detail.getMeetingPlace(),
                canManage && auto ? detail.getDepartureLatitude() : null,
                canManage && auto ? detail.getDepartureLongitude() : null,
                detail.getDestinationResort().getId(),
                detail.getDestinationResort().getName(),
                detail.getTripType(),
                detail.getDepartureAt(),
                detail.getReturnAt(),
                detail.getPassengerCapacity(),
                auto ? detail.getFuelType() : null,
                auto ? detail.getFuelEfficiency() : null,
                detail.getCostMode(),
                auto ? detail.getFuelPrice() : null,
                auto ? detail.getFuelPriceSource() : null,
                auto ? detail.getFuelPriceObservedAt() : null,
                auto ? detail.getRouteDistanceKm() : null,
                auto ? detail.getRouteTollFee() : null,
                auto ? detail.getEstimatedFuelCost() : null,
                auto ? detail.getEstimatedTotalCost() : null,
                detail.getEstimatedCostPerPerson(),
                auto ? detail.getRouteSource() : null,
                auto ? detail.getRouteCalculatedAt() : null,
                contactInfo,
                detail.isContactPublicToGuest(),
                detail.isEquipmentLoadAvailable(),
                canManage,
                post.getCreatedAt());
    }
}
