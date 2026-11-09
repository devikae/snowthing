package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.admin.entity.ChatBannedIp;

public record ChatBannedIpResponse(
        Long id,
        String bannedIp,
        String reason,
        LocalDateTime bannedUntil,
        String createdBy,
        LocalDateTime createdAt,
        boolean expired) {

    public static ChatBannedIpResponse from(ChatBannedIp entity) {
        return new ChatBannedIpResponse(
                entity.getId(),
                entity.getBannedIp(),
                entity.getReason(),
                entity.getBannedUntil(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.isExpired());
    }
}
