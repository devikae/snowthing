package com.ikae.snowthing.domain.image.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostImagePurgeRequest(@NotBlank @Size(max = 200) String reason) {}
