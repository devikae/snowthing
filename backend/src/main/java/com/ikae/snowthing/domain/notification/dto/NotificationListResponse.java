package com.ikae.snowthing.domain.notification.dto;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record NotificationListResponse(
        List<NotificationResponse> items,
        @JsonSerialize(using = ToStringSerializer.class) Long nextCursor,
        boolean hasNext) {
    public NotificationListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
