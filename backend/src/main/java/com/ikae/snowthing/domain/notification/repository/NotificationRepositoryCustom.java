package com.ikae.snowthing.domain.notification.repository;

import java.util.List;

import com.ikae.snowthing.domain.notification.entity.Notification;

public interface NotificationRepositoryCustom {

    /** 받는 사람의 알림을 ID 내림차순으로 최대 {@code limit}건. {@code cursor}가 있으면 그보다 작은 ID만 본다. */
    List<Notification> findPage(Long receiverId, boolean unreadOnly, Long cursor, int limit);
}
