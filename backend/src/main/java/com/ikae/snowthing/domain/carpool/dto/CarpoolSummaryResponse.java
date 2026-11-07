package com.ikae.snowthing.domain.carpool.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.carpool.entity.CarpoolCostMode;
import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.carpool.entity.CarpoolTripType;
import com.ikae.snowthing.domain.member.entity.Member;
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
        CarpoolCostMode costMode,
        int estimatedCostPerPerson,
        boolean equipmentLoadAvailable,
        LocalDateTime createdAt) {

    public static CarpoolSummaryResponse from(CarpoolDetail detail) {
        Post post = detail.getPost();
        Member writer = post.getMember();
        String writerName =
                writer == null || writer.isWithdrawn()
                        ? Member.WITHDRAWN_NICKNAME
                        : writer.getNickname();
        return new CarpoolSummaryResponse(
                post.getPublicId(),
                post.getTitle(),
                writerName,
                detail.getDepartureRegion(),
                detail.getDestinationResort().getName(),
                detail.getTripType(),
                detail.getDepartureAt(),
                detail.getPassengerCapacity(),
                detail.getCostMode(),
                detail.getEstimatedCostPerPerson(),
                detail.isEquipmentLoadAvailable(),
                post.getCreatedAt());
    }
}
