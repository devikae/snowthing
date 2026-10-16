package com.ikae.snowthing.domain.notification.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "notification",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_notification_public_id", columnNames = "public_id"),
            @UniqueConstraint(
                    name = "uk_notification_idempotency_key", columnNames = "idempotency_key")
        },
        indexes = {
            @Index(
                    name = "idx_notification_recipient_id_desc",
                    columnList = "recipient_member_id,notification_id"),
            @Index(
                    name = "idx_notification_recipient_read_id",
                    columnList = "recipient_member_id,read_at,notification_id"),
            @Index(name = "idx_notification_retention", columnList = "read_at,created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_member_id", nullable = false)
    private Member recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 150)
    private String idempotencyKey;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    private Notification(
            Member recipient,
            NotificationType type,
            Post post,
            Comment comment,
            String idempotencyKey) {
        this.publicId = UUID.randomUUID().toString();
        this.recipient = recipient;
        this.type = type;
        this.post = post;
        this.comment = comment;
        this.idempotencyKey = idempotencyKey;
    }

    public static Notification create(
            Member recipient,
            NotificationType type,
            Post post,
            Comment comment,
            String idempotencyKey) {
        return new Notification(recipient, type, post, comment, idempotencyKey);
    }

    public void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now();
        }
    }
}
