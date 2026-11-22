package com.ikae.snowthing.domain.moderation.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * AI 자동 검열 대기열과 판정 기록 (IKA-80, 062). 읽기와 쓰기는 {@code ContentModerationRepository}가 JDBC로 한다. 이 엔티티는
 * 테스트 스키마(create-drop)와 운영 validate용 스키마 정의다. 본문은 저장하지 않는다.
 */
@Entity
@Table(
        name = "content_moderation",
        indexes = {
            @Index(
                    name = "idx_content_moderation_due",
                    columnList = "status,next_attempt_at,content_moderation_id"),
            @Index(
                    name = "idx_content_moderation_review",
                    columnList = "verdict,reviewed_at,content_moderation_id"),
            @Index(name = "idx_content_moderation_target", columnList = "target_type,target_id"),
            @Index(name = "idx_content_moderation_hash", columnList = "content_hash,created_at"),
            @Index(name = "idx_content_moderation_processed", columnList = "processed_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentModeration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_moderation_id")
    private Long id;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "writer_ip", length = 45)
    private String writerIp;

    @Column(name = "content_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String contentHash;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "stage", length = 20)
    private String stage;

    @Column(name = "verdict", length = 10)
    private String verdict;

    @Column(name = "reasons", length = 1000)
    private String reasons;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_result", length = 20)
    private String reviewResult;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
