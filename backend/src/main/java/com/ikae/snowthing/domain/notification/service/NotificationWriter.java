package com.ikae.snowthing.domain.notification.service;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.event.CommentCreatedEvent;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.notification.entity.Notification;
import com.ikae.snowthing.domain.notification.entity.NotificationType;
import com.ikae.snowthing.domain.notification.event.NotificationsCreatedEvent;
import com.ikae.snowthing.domain.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 댓글 이벤트로 알림을 저장한다. 댓글 트랜잭션이 커밋된 뒤 불리므로 항상 새 트랜잭션을 연다.
 *
 * <p>AFTER_COMMIT 시점에는 원래 트랜잭션의 커넥션이 아직 묶여 있어 같은 트랜잭션으로 쓰면 커밋되지 않는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int createForComment(CommentCreatedEvent event) {
        Map<Long, NotificationType> recipients = NotificationRecipients.resolve(event);
        if (recipients.isEmpty()) {
            return 0;
        }
        List<Notification> notifications =
                memberRepository.findAllById(recipients.keySet()).stream()
                        .filter(member -> member.getStatus() != MemberStatus.WITHDRAWN)
                        .map(
                                member ->
                                        toNotification(
                                                member, recipients.get(member.getId()), event))
                        .toList();
        notificationRepository.saveAll(notifications);
        // 이 새 트랜잭션이 커밋된 뒤에 웹 푸시 리스너가 받는다.
        eventPublisher.publishEvent(NotificationsCreatedEvent.of(notifications));
        return notifications.size();
    }

    private static Notification toNotification(
            Member receiver, NotificationType type, CommentCreatedEvent event) {
        return Notification.create(
                receiver,
                type,
                event.postId(),
                event.postPublicId(),
                event.postCategoryCode(),
                event.commentId(),
                event.actorName(),
                event.preview());
    }
}
