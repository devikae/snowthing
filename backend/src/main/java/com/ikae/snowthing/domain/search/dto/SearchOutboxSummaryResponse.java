package com.ikae.snowthing.domain.search.dto;

import java.time.LocalDateTime;

/**
 * outbox 상태별 건수와 가장 오래 기다린 PENDING 이벤트. 시각은 KST.
 *
 * @param oldestPendingAgeSeconds PENDING이 없으면 null
 */
public record SearchOutboxSummaryResponse(
        long pendingCount,
        long doneCount,
        long failedCount,
        LocalDateTime oldestPendingCreatedAt,
        Long oldestPendingAgeSeconds,
        LocalDateTime checkedAt) {}
