package com.ikae.snowthing.domain.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ikae.snowthing.domain.comment.event.CommentCreatedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 커밋된 댓글만 알림으로 만든다. 롤백된 댓글에는 AFTER_COMMIT이 불리지 않는다.
 *
 * <p>알림은 부가 기능이라 저장이 실패해도 이미 커밋된 댓글에는 영향이 없고 경고 로그만 남긴다. 새 트랜잭션은 {@link NotificationWriter}가 열어서 그
 * 커밋 실패까지 여기서 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationWriter notificationWriter;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommentCreated(CommentCreatedEvent event) {
        try {
            notificationWriter.createForComment(event);
        } catch (RuntimeException e) {
            log.warn(
                    "댓글 알림 저장 실패 commentId={} cause={}",
                    event.commentId(),
                    e.getClass().getSimpleName(),
                    e);
        }
    }
}
