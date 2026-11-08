package com.ikae.snowthing.domain.notification.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ikae.snowthing.domain.notification.entity.Notification;
import com.ikae.snowthing.domain.notification.entity.NotificationType;

public record NotificationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long notificationId,
        NotificationType type,
        String postPublicId,
        String postCategoryCode,
        @JsonSerialize(using = ToStringSerializer.class) Long commentId,
        String actorName,
        String preview,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getPostPublicId(),
                notification.getPostCategoryCode(),
                notification.getCommentId(),
                notification.getActorName(),
                notification.getPreview(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
