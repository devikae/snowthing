package com.ikae.snowthing.domain.push.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.ikae.snowthing.domain.notification.event.NotificationsCreatedEvent;
import com.ikae.snowthing.domain.notification.event.NotificationsCreatedEvent.Item;
import com.ikae.snowthing.domain.push.entity.PushSubscription;
import com.ikae.snowthing.domain.push.repository.PushSubscriptionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * 알림이 커밋된 뒤 받는 사람의 기기들로 웹 푸시를 보낸다.
 *
 * <p>AFTER_COMMIT이라 롤백된 알림은 보내지 않는다. {@code @Async}로 따로 돈 스레드에서 보내므로 댓글 작성이나 운영 조치 요청은 푸시 서비스 응답을
 * 기다리지 않는다. 여기서 무엇이 실패해도 이미 저장된 알림에는 영향이 없고 경고 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final VapidKeys vapidKeys;
    private final PushSubscriptionRepository subscriptionRepository;
    private final WebPushSender sender;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Async("webPushTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationsCreated(NotificationsCreatedEvent event) {
        if (!vapidKeys.enabled() || event.items().isEmpty()) {
            return;
        }
        try {
            dispatch(event);
        } catch (RuntimeException e) {
            log.warn("웹 푸시 처리 실패 cause={}", e.getClass().getSimpleName(), e);
        }
    }

    void dispatch(NotificationsCreatedEvent event) {
        Map<Long, Item> byReceiver = new LinkedHashMap<>();
        event.items().forEach(item -> byReceiver.putIfAbsent(item.receiverId(), item));
        List<PushSubscription> subscriptions =
                subscriptionRepository.findAllByMemberIdInOrderByIdAsc(byReceiver.keySet());
        if (subscriptions.isEmpty()) {
            return;
        }
        List<Long> delivered = new ArrayList<>();
        List<Long> gone = new ArrayList<>();
        for (PushSubscription subscription : subscriptions) {
            byte[] payload =
                    objectMapper.writeValueAsBytes(
                            PushMessages.of(byReceiver.get(subscription.getMemberId())));
            switch (sender.send(subscription, payload)) {
                case DELIVERED -> delivered.add(subscription.getId());
                case GONE -> gone.add(subscription.getId());
                case FAILED -> {}
            }
        }
        if (!gone.isEmpty()) {
            subscriptionRepository.deleteByIds(gone);
        }
        if (!delivered.isEmpty()) {
            subscriptionRepository.touch(delivered, LocalDateTime.now(clock.withZone(KST)));
        }
    }
}
