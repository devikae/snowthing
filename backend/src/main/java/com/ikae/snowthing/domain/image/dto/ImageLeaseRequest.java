package com.ikae.snowthing.domain.image.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ImageLeaseRequest(@NotNull @Size(max = 10) List<String> imageKeys) {
    public ImageLeaseRequest {
        if (imageKeys != null) imageKeys = List.copyOf(imageKeys);
    }
}
