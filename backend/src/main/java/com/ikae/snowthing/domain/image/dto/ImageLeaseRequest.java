package com.ikae.snowthing.domain.image.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ImageLeaseRequest(
        @NotNull @Size(max = 10)
                List<@jakarta.validation.constraints.NotBlank @Size(max = 512) String> imageKeys) {
    public ImageLeaseRequest {
        if (imageKeys != null && imageKeys.stream().anyMatch(java.util.Objects::isNull))
            throw new com.ikae.snowthing.global.exception.CustomException(
                    com.ikae.snowthing.global.error.ErrorCode.INVALID_INPUT);
        if (imageKeys != null) imageKeys = List.copyOf(imageKeys);
    }
}
