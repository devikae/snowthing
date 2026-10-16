package com.ikae.snowthing.domain.notification.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.market.repository.MarketListingRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.notification.dto.NotificationListResponse;
import com.ikae.snowthing.domain.notification.dto.NotificationResponse;
import com.ikae.snowthing.domain.notification.dto.UnreadCountResponse;
import com.ikae.snowthing.domain.notification.entity.Notification;
import com.ikae.snowthing.domain.notification.entity.NotificationType;
import com.ikae.snowthing.domain.notification.repository.NotificationRepository;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private static final int BEST_LIKE_COUNT = 30;

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final MarketListingRepository marketListingRepository;
    private final Clock clock;

    @Transactional
    public void notifyCommentCreated(Post post, Comment comment, Member actor) {
        if (marketListingRepository.existsByPostId(post.getId())) {
            return;
        }
        Member recipient;
        NotificationType type;
        if (comment.getParent() == null) {
            recipient = post.getMember();
            type = NotificationType.POST_COMMENT;
        } else {
            recipient = comment.rootParent().getMember();
            type = NotificationType.COMMENT_REPLY;
        }
        if (recipient == null || (actor != null && recipient.getId().equals(actor.getId()))) {
            return;
        }
        String key = type.name() + ":" + comment.getId() + ":" + recipient.getId();
        notificationRepository.save(Notification.create(recipient, type, post, comment, key));
    }

    @Transactional
    public void notifyPostBecameBest(Post post, Long actorMemberId, int resultingLikeCount) {
        if (resultingLikeCount != BEST_LIKE_COUNT || post.getMember() == null) {
            return;
        }
        if (actorMemberId != null && post.getMember().getId().equals(actorMemberId)) {
            return;
        }
        Long recipientId = post.getMember().getId();
        String key = NotificationType.POST_BEST.name() + ":" + post.getId() + ":" + recipientId;
        notificationRepository.save(
                Notification.create(
                        post.getMember(), NotificationType.POST_BEST, post, null, key));
    }

    @Transactional(readOnly = true)
    public NotificationListResponse list(
            CustomUserDetails userDetails, String cursor, Integer requestedSize) {
        Member member = findMember(userDetails);
        int size = requestedSize == null ? DEFAULT_SIZE : requestedSize;
        if (size < 1 || size > MAX_SIZE) {
            throw new CustomAuthException(ErrorCode.NOTIFICATION_INVALID_PAGE_SIZE);
        }
        List<Notification> fetched;
        if (cursor == null || cursor.isBlank()) {
            fetched = notificationRepository.findByRecipientIdOrderByIdDesc(
                    member.getId(), PageRequest.of(0, size + 1));
        } else {
            Notification cursorNotification =
                    notificationRepository
                            .findByPublicIdAndRecipientId(cursor, member.getId())
                            .orElseThrow(
                                    () ->
                                            new CustomAuthException(
                                                    ErrorCode.NOTIFICATION_INVALID_CURSOR));
            fetched =
                    notificationRepository.findByRecipientIdAndIdLessThanOrderByIdDesc(
                            member.getId(), cursorNotification.getId(), PageRequest.of(0, size + 1));
        }
        boolean hasNext = fetched.size() > size;
        List<Notification> page = hasNext ? fetched.subList(0, size) : fetched;
        List<NotificationResponse> responses = page.stream().map(NotificationResponse::from).toList();
        String nextCursor = hasNext ? page.getLast().getPublicId() : null;
        return new NotificationListResponse(responses, nextCursor, hasNext);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(CustomUserDetails userDetails) {
        return new UnreadCountResponse(
                notificationRepository.countByRecipientIdAndReadAtIsNull(findMember(userDetails).getId()));
    }

    @Transactional
    public void markRead(CustomUserDetails userDetails, String publicId) {
        Notification notification = findOwned(userDetails, publicId);
        notification.markRead();
    }

    @Transactional
    public void markAllRead(CustomUserDetails userDetails) {
        notificationRepository.markAllRead(findMember(userDetails).getId());
    }

    @Transactional
    @Scheduled(cron = "${snowthing.notification.cleanup-cron:0 50 4 * * *}", zone = "Asia/Seoul")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST_ZONE));
        notificationRepository.deleteReadBefore(now.minusDays(30));
        notificationRepository.deleteUnreadBefore(now.minusDays(90));
    }

    private Notification findOwned(CustomUserDetails userDetails, String publicId) {
        return notificationRepository
                .findByPublicIdAndRecipientId(publicId, findMember(userDetails).getId())
                .orElseThrow(() -> new CustomAuthException(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    private Member findMember(CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
        }
        return memberRepository
                .findByPublicId(userDetails.getPublicId())
                .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
