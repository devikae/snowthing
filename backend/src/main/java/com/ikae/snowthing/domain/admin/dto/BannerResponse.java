package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.admin.entity.Banner;

public record BannerResponse(
        Long id,
        String title,
        String imageUrl,
        String linkUrl,
        int displayOrder,
        boolean isActive,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime createdAt) {

    public static BannerResponse from(Banner entity) {
        return new BannerResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getImageUrl(),
                entity.getLinkUrl(),
                entity.getDisplayOrder(),
                entity.isActive(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getCreatedAt());
    }
}
