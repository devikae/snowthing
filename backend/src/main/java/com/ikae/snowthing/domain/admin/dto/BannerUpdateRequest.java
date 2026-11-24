package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BannerUpdateRequest(
        @NotBlank @Size(max = 100) String title,
        @NotBlank
                @Size(max = BannerUrlPolicy.MAX_LENGTH)
                @Pattern(regexp = BannerUrlPolicy.REGEXP, message = BannerUrlPolicy.MESSAGE)
                String imageUrl,
        @Size(max = BannerUrlPolicy.MAX_LENGTH)
                @Pattern(regexp = BannerUrlPolicy.REGEXP, message = BannerUrlPolicy.MESSAGE)
                String linkUrl,
        int displayOrder,
        boolean isActive,
        LocalDateTime startAt,
        LocalDateTime endAt) {}
