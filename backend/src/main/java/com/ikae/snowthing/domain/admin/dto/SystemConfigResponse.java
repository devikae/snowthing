package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.admin.entity.SystemConfig;

public record SystemConfigResponse(
        String key,
        String value,
        String valueType,
        String description,
        LocalDateTime updatedAt,
        String updatedBy) {

    public static SystemConfigResponse from(SystemConfig entity) {
        return new SystemConfigResponse(
                entity.getKey(),
                entity.getValue(),
                entity.getValueType(),
                entity.getDescription(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy());
    }
}
