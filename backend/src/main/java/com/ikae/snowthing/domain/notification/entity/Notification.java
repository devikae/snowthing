package com.ikae.snowthing.domain.notification.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.ikae.snowthing.domain.member.entity.Member;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 받는 사람 한 명에게 가는 알림 한 건. 글과 댓글은 링크용 스냅샷이라 FK 없이 값으로 저장한다.
 *
 * <p>작성자 이름과 미리보기도 만든 시점 값으로 고정한다. 원본 댓글이 수정되거나 삭제돼도 알림은 바뀌지 않는다.
 */
@Entity
@Table(
        name = "notification",
        indexes =
                @Index(
                        name = "idx_notification_receiver_read_id",
                        columnList = "receiver_id,is_read,notification_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "receiver_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_notification_receiver"))
    private Member receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "post_public_id", nullable = false, length = 36)
    private String postPublicId;

    /** 화면 이동 경로를 고르는 데 쓴다. 중고장터 글은 /market, 나머지는 /posts 상세로 간다. */
    @Column(name = "post_category_code", nullable = false, length = 30)
    private String postCategoryCode;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "actor_name", nullable = false, length = 50)
    private String actorName;

    @Column(nullable = false, length = 100)
    private String preview;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Notification(
            Member receiver,
            NotificationType type,
            Long postId,
            String postPublicId,
            String postCategoryCode,
            Long commentId,
            String actorName,
            String preview) {
        this.receiver = receiver;
        this.type = type;
        this.postId = postId;
        this.postPublicId = postPublicId;
        this.postCategoryCode = postCategoryCode;
        this.commentId = commentId;
        this.actorName = actorName;
        this.preview = preview;
        this.read = false;
    }

    public static Notification create(
            Member receiver,
            NotificationType type,
            Long postId,
            String postPublicId,
            String postCategoryCode,
            Long commentId,
            String actorName,
            String preview) {
        return new Notification(
                receiver,
                type,
                postId,
                postPublicId,
                postCategoryCode,
                commentId,
                actorName,
                preview);
    }

    /** 이미 읽은 알림이면 그대로 둔다. */
    public void markRead() {
        this.read = true;
    }
}
