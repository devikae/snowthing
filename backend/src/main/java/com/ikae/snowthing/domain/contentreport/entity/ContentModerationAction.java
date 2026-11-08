package com.ikae.snowthing.domain.contentreport.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.member.entity.Member;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 조치 이력. 임시조치 행만 해제 시각을 기록하고 나머지는 바뀌지 않는다. */
@Entity
@Table(name = "content_moderation_action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentModerationAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_moderation_action_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ModerationActionType actionType;

    @Column(length = 500)
    private String memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Member admin;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ContentModerationAction(
            ReportTargetType targetType,
            Long targetId,
            ModerationActionType actionType,
            String memo,
            Member admin,
            LocalDateTime expiresAt,
            LocalDateTime createdAt) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.actionType = actionType;
        this.memo = memo;
        this.admin = admin;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public void release(LocalDateTime now) {
        this.releasedAt = now;
    }
}
