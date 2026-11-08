package com.ikae.snowthing.domain.notification.repository;

import static com.ikae.snowthing.domain.notification.entity.QNotification.notification;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.ikae.snowthing.domain.notification.entity.Notification;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Notification> findPage(
            Long receiverId, boolean unreadOnly, Long cursor, int limit) {
        BooleanBuilder where = new BooleanBuilder(notification.receiver.id.eq(receiverId));
        if (unreadOnly) {
            where.and(notification.read.isFalse());
        }
        if (cursor != null) {
            where.and(notification.id.lt(cursor));
        }
        return queryFactory
                .selectFrom(notification)
                .where(where)
                .orderBy(notification.id.desc())
                .limit(limit)
                .fetch();
    }
}
