package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;

public record ChatBannedIpCreateRequest(
        @NotBlank String bannedIp, @NotBlank String reason, LocalDateTime bannedUntil) {}
