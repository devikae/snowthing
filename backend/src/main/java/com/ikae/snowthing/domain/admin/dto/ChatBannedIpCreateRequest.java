package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 컬럼 길이(banned_ip 45, reason 255)를 넘으면 DB 오류 대신 400으로 거절한다. */
public record ChatBannedIpCreateRequest(
        @NotBlank @Size(max = 45) String bannedIp,
        @NotBlank @Size(max = 255) String reason,
        LocalDateTime bannedUntil) {}
