package com.ikae.snowthing.domain.notification.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.notification.entity.Notification;
import com.ikae.snowthing.domain.notification.entity.NotificationType;
import com.ikae.snowthing.domain.post.entity.PostStatus;

public record NotificationResponse(
        String publicId,
        NotificationType type,
        String message,
        String postPublicId,
        String postTitle,
        String targetUrl,
        boolean targetAvailable,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        boolean available =
                !notification.getPost().isDeleted()
                        && PostStatus.NORMAL.equals(notification.getPost().getStatus());
        String postPublicId = available ? notification.getPost().getPublicId() : null;
        String postTitle = available ? notification.getPost().getTitle() : null;
        String targetUrl =
                available
                        ? "CARPOOL".equals(notification.getPost().getCategory().getCode())
                                ? "/carpool/" + postPublicId
                                : "/posts/" + postPublicId
                        : null;
        return new NotificationResponse(
                notification.getPublicId(),
                notification.getType(),
                message(notification.getType()),
                postPublicId,
                postTitle,
                targetUrl,
                available,
                notification.getReadAt() != null,
                notification.getCreatedAt());
    }

    private static String message(NotificationType type) {
        return switch (type) {
            case POST_COMMENT -> "작성한 게시글에 새 댓글이 달렸습니다.";
            case COMMENT_REPLY -> "작성한 댓글에 새 답글이 달렸습니다.";
            case POST_BEST -> "작성한 게시글이 실시간 베스트에 올랐습니다.";
        };
    }
}
