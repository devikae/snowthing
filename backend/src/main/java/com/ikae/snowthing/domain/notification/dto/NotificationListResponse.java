package com.ikae.snowthing.domain.notification.dto;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> notifications, String nextCursor, boolean hasNext) {
    public NotificationListResponse {
        notifications = List.copyOf(notifications);
    }
}
