package com.ikae.snowthing.domain.search.dto;

import java.time.LocalDateTime;

/**
 * 재인덱싱 체크포인트 상태.
 *
 * @param status RUNNING(중간에 멈춤, 다시 부르면 이어서 함) 또는 DONE
 * @param resumed 이전 RUNNING 체크포인트에서 이어서 했는지
 * @param batches 이번 호출에서 넣은 배치 수
 * @param lastPostId 마지막으로 outbox에 넣은 게시글 내부 ID (체크포인트)
 * @param enqueuedCount 이 작업이 지금까지 넣은 게시글 이벤트 수
 */
public record SearchReindexResponse(
        String status,
        boolean resumed,
        int batches,
        long lastPostId,
        long enqueuedCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {}
