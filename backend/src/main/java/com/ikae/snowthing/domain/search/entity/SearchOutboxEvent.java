package com.ikae.snowthing.domain.search.entity;

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
import lombok.NoArgsConstructor;

/**
 * 근거 검색 색인 outbox (028). 읽기·쓰기는 {@code SearchOutboxRepository}가 JDBC로 한다. 이 엔티티는 테스트
 * 스키마(create-drop)와 운영 validate용 스키마 정의다.
 */
@Entity
@Table(
        name = "search_outbox",
        indexes = {
            @Index(name = "idx_search_outbox_status_due", columnList = "status,due_at"),
            @Index(name = "idx_search_outbox_source", columnList = "source_type,source_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SearchSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "operation", nullable = false, length = 20)
    private String operation;

    @Column(name = "source_version", nullable = false)
    private long sourceVersion;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "lease_until")
    private LocalDateTime leaseUntil;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;
}
