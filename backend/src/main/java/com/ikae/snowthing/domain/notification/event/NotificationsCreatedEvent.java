package com.ikae.snowthing.domain.notification.event;

import java.util.Collection;
import java.util.List;

import com.ikae.snowthing.domain.notification.entity.Notification;
import com.ikae.snowthing.domain.notification.entity.NotificationType;

/**
 * 알림이 저장됐다는 사실. 커밋 뒤 비동기로 웹 푸시를 보내는 리스너가 엔티티 없이 쓰도록 필요한 값만 담는다.
 *
 * <p>작성자 이름과 미리보기는 담지 않는다. 푸시는 잠금 화면에 그대로 보이므로 종류와 이동 경로만 보낸다.
 */
public record NotificationsCreatedEvent(List<Item> items) {

    public record Item(
            Long receiverId, NotificationType type, String postPublicId, String postCategoryCode) {}

    public static NotificationsCreatedEvent of(Collection<Notification> notifications) {
        return new NotificationsCreatedEvent(
                notifications.stream()
                        .map(
                                notification ->
                                        new Item(
                                                notification.getReceiver().getId(),
                                                notification.getType(),
                                                notification.getPostPublicId(),
                                                notification.getPostCategoryCode()))
                        .toList());
    }
}
