package com.ikae.snowthing.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record SystemConfigUpdateRequest(@NotBlank String value) {}
