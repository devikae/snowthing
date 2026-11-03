package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public record BannerCreateRequest(
        @NotBlank String title,
        @NotBlank String imageUrl,
        String linkUrl,
        int displayOrder,
        boolean isActive,
        LocalDateTime startAt,
        LocalDateTime endAt) {}
