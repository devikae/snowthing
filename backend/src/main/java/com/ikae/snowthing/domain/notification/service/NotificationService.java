package com.ikae.snowthing.domain.notification.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.notification.dto.NotificationListResponse;
import com.ikae.snowthing.domain.notification.dto.NotificationResponse;
import com.ikae.snowthing.domain.notification.dto.UnreadCountResponse;
import com.ikae.snowthing.domain.notification.entity.Notification;
import com.ikae.snowthing.domain.notification.repository.NotificationRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.ApiTokenUserDetails;
import com.ikae.snowthing.global.security.BotDelegatedUserDetails;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 내 알림 조회와 읽음 처리. 로그인 세션의 회원만 쓴다.
 *
 * <p>위임 토큰(라이브톡 봇)과 API 토큰은 회원 대신 움직이는 자동화라 개인 알림을 읽거나 읽음으로 바꾸지 못하게 401로 막는다.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(
            CustomUserDetails userDetails, boolean unreadOnly, Long cursor, Integer size) {
        Long receiverId = requireMemberId(userDetails);
        int pageSize = normalizeSize(size);
        List<Notification> rows =
                notificationRepository.findPage(receiverId, unreadOnly, cursor, pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<Notification> page = hasNext ? rows.subList(0, pageSize) : rows;
        Long nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
        return new NotificationListResponse(
                page.stream().map(NotificationResponse::from).toList(), nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(CustomUserDetails userDetails) {
        return new UnreadCountResponse(
                notificationRepository.countByReceiver_IdAndReadFalse(
                        requireMemberId(userDetails)));
    }

    /** 남의 알림은 있는지 드러내지 않도록 없는 알림과 같은 404로 응답한다. 이미 읽었으면 그대로 둔다. */
    @Transactional
    public void markRead(CustomUserDetails userDetails, Long notificationId) {
        Notification notification =
                notificationRepository
                        .findByIdAndReceiver_Id(notificationId, requireMemberId(userDetails))
                        .orElseThrow(
                                () -> new CustomAuthException(ErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
    }

    @Transactional
    public void markAllRead(CustomUserDetails userDetails) {
        notificationRepository.markAllRead(requireMemberId(userDetails));
    }

    private static int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static Long requireMemberId(CustomUserDetails userDetails) {
        if (userDetails == null
                || userDetails instanceof BotDelegatedUserDetails
                || userDetails instanceof ApiTokenUserDetails
                || userDetails.getMember() == null
                || userDetails.getMember().getId() == null) {
            throw new CustomAuthException(ErrorCode.NOTIFICATION_LOGIN_REQUIRED);
        }
        return userDetails.getMember().getId();
    }
}
