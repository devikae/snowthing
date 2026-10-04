package com.ikae.snowthing.domain.carpool.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.post.entity.Post;

public record CarpoolSummaryResponse(
        String publicId,
        String title,
        String writerName,
        String departureRegion,
        String destinationResortName,
        CarpoolTripType tripType,
        LocalDateTime departureAt,
        int passengerCapacity,
        int estimatedCostPerPerson,
        boolean equipmentLoadAvailable,
        LocalDateTime createdAt) {

    public static CarpoolSummaryResponse from(CarpoolDetail detail) {
        Post post = detail.getPost();
        String writerName = post.getMember() == null ? "탈퇴한 회원" : post.getMember().getNickname();
        return new CarpoolSummaryResponse(
                post.getPublicId(),
                post.getTitle(),
                writerName,
                detail.getDepartureRegion(),
                detail.getDestinationResort().getName(),
                detail.getTripType(),
                detail.getDepartureAt(),
                detail.getPassengerCapacity(),
                detail.getEstimatedCostPerPerson(),
                detail.isEquipmentLoadAvailable(),
                post.getCreatedAt());
    }
}
