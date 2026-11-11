package com.ikae.snowthing.domain.admin.log;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자 조치 기록 (050 마이그레이션). 쓰기만 하고 바꾸지 않는다. 글 본문은 넣지 않는다. */
@Entity
@Table(
        name = "admin_action_log",
        indexes = {
            @Index(name = "idx_admin_action_log_action", columnList = "action,admin_action_log_id"),
            @Index(
                    name = "idx_admin_action_log_target",
                    columnList = "target_type,target_id,admin_action_log_id"),
            @Index(
                    name = "idx_admin_action_log_actor",
                    columnList = "actor_member_id,admin_action_log_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminActionLog {

    static final int MAX_REASON_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_action_log_id")
    private Long id;

    @Column(name = "actor_member_id")
    private Long actorMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AdminActionType action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private AdminTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(name = "reason", length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    AdminActionLog(
            Long actorMemberId,
            AdminActionType action,
            AdminTargetType targetType,
            String targetId,
            String reason,
            LocalDateTime createdAt) {
        this.actorMemberId = actorMemberId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.createdAt = createdAt;
    }
}
