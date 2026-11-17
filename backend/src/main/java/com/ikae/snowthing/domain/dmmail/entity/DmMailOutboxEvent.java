package com.ikae.snowthing.domain.dmmail.entity;

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
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 쪽지 도착 메일 발송 요청 (053). 쪽지 1개에 행 1개다. 읽기와 쓰기는 {@code DmMailOutboxRepository}가 JDBC로 한다. 이 엔티티는 테스트
 * 스키마(create-drop)와 운영 validate용 스키마 정의다.
 */
@Entity
@Table(
        name = "dm_mail_outbox",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_dm_mail_outbox_intent",
                        columnNames = {
                            "direct_message_id",
                            "recipient_member_id",
                            "event_type",
                            "channel"
                        }),
        indexes = {
            @Index(name = "idx_dm_mail_outbox_due", columnList = "status,due_at,event_id"),
            @Index(name = "idx_dm_mail_outbox_recipient", columnList = "recipient_member_id,status")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DmMailOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "direct_message_id", nullable = false)
    private Long directMessageId;

    @Column(name = "recipient_member_id", nullable = false)
    private Long recipientMemberId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DmMailStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "claim_token", length = 36, columnDefinition = "CHAR(36)")
    private String claimToken;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "skip_reason", length = 30)
    private String skipReason;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
