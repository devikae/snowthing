package com.ikae.snowthing.domain.notification.entity;

/** 알림 종류. 한 댓글이 같은 사람에게 여러 종류로 해당하면 선언 순서가 앞인 것 하나만 보낸다. */
public enum NotificationType {
    INQUIRY_ANSWER,
    COMMENT_REPLY,
    POST_COMMENT,
    MODERATION_RESULT
}
