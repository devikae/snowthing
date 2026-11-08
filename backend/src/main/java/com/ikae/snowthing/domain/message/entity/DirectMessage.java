package com.ikae.snowthing.domain.message.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 간 1:1 쪽지 (034). 편지함형이라 쪽지 한 통이 행 하나이고, 발신자와 수신자가 각자 자기 쪽에서만 지운다.
 *
 * <p>판매글은 ID만 들고 연관으로 잡지 않는다. 판매글의 게시글(Post)에는 {@code @SQLRestriction("is_deleted = false")}가 있어
 * 삭제된 판매글을 연관으로 따라가면 엔티티를 찾지 못한다. 화면 표시용 판매글 정보는 서비스가 ID 묶음으로 한 번에 조회한다.
 */
@Entity
@Table(
        name = "direct_message",
        indexes = {
            @Index(
                    name = "idx_direct_message_receiver",
                    columnList = "receiver_id, receiver_deleted_at, direct_message_id"),
            @Index(
                    name = "idx_direct_message_sender",
                    columnList = "sender_id, sender_deleted_at, direct_message_id"),
            @Index(name = "idx_direct_message_market_listing", columnList = "market_listing_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DirectMessage extends BaseTimeEntity {

    public static final int MAX_CONTENT_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "direct_message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;

    @Column(name = "market_listing_id")
    private Long marketListingId;

    @Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "sender_deleted_at")
    private LocalDateTime senderDeletedAt;

    @Column(name = "receiver_deleted_at")
    private LocalDateTime receiverDeletedAt;

    public DirectMessage(Member sender, Member receiver, Long marketListingId, String content) {
        this.sender = Objects.requireNonNull(sender);
        this.receiver = Objects.requireNonNull(receiver);
        this.marketListingId = marketListingId;
        this.content = Objects.requireNonNull(content);
    }

    public boolean isSender(Long memberId) {
        return sender.getId().equals(memberId);
    }

    public boolean isReceiver(Long memberId) {
        return receiver.getId().equals(memberId);
    }

    /** 이 회원의 쪽지함에 남아 있는지. 남의 쪽지이거나 내 쪽에서 지운 쪽지는 false. */
    public boolean isVisibleTo(Long memberId) {
        return (isSender(memberId) && senderDeletedAt == null)
                || (isReceiver(memberId) && receiverDeletedAt == null);
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** 수신자가 처음 열 때만 읽은 시각을 기록한다. 발신자가 열거나 다시 열면 바뀌지 않는다. */
    public void markReadBy(Long memberId, LocalDateTime now) {
        if (readAt == null && isReceiver(memberId)) {
            readAt = now;
        }
    }

    /** 요청한 회원 쪽에서만 지운다. 자기에게 보낸 쪽지는 만들 수 없으므로 두 쪽이 겹치지 않는다. */
    public void deleteBy(Long memberId, LocalDateTime now) {
        if (isSender(memberId) && senderDeletedAt == null) {
            senderDeletedAt = now;
        } else if (isReceiver(memberId) && receiverDeletedAt == null) {
            receiverDeletedAt = now;
        }
    }
}
