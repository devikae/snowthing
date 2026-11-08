package com.ikae.snowthing.domain.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ikae.snowthing.domain.comment.event.CommentCreatedEvent;
import com.ikae.snowthing.domain.notification.entity.NotificationType;
import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;

/**
 * 댓글 하나로 누구에게 어떤 알림을 보낼지 정한다. 회원 상태(탈퇴)는 DB를 봐야 해서 여기서 거르지 않는다.
 *
 * <ul>
 *   <li>1:1 문의 글에 관리자가 댓글이나 답글: 문의 작성자에게 {@code INQUIRY_ANSWER}
 *   <li>답글: 부모 댓글 작성자에게 {@code COMMENT_REPLY}
 *   <li>댓글과 답글: 글 작성자에게 {@code POST_COMMENT}
 * </ul>
 *
 * <p>같은 사람이 여러 규칙에 걸리면 위 순서(우선순위)로 먼저 정해진 하나만 남긴다. 비회원(ID 없음)과 댓글 작성자 본인은 뺀다.
 */
final class NotificationRecipients {

    private NotificationRecipients() {}

    static Map<Long, NotificationType> resolve(CommentCreatedEvent event) {
        Map<Long, NotificationType> recipients = new LinkedHashMap<>();
        if (event.actorAdmin() && PostCategoryCodes.isInquiry(event.postCategoryCode())) {
            add(recipients, event, event.postAuthorId(), NotificationType.INQUIRY_ANSWER);
        }
        add(recipients, event, event.parentAuthorId(), NotificationType.COMMENT_REPLY);
        add(recipients, event, event.postAuthorId(), NotificationType.POST_COMMENT);
        return recipients;
    }

    private static void add(
            Map<Long, NotificationType> recipients,
            CommentCreatedEvent event,
            Long receiverId,
            NotificationType type) {
        if (receiverId == null || receiverId.equals(event.actorId())) {
            return;
        }
        recipients.putIfAbsent(receiverId, type);
    }
}
