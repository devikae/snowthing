package com.ikae.snowthing.domain.dmmail.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.ikae.snowthing.domain.dmmail.entity.DmMailStatus;

/**
 * 쪽지 메일 발송 요청 상태별 건수와 가장 오래 기다린 미완료 요청. 시각은 KST.
 *
 * @param counts 상태별 건수. 없는 상태는 0
 * @param oldestOpenCreatedAt PENDING, RETRY_WAIT, PROCESSING 중 가장 오래된 생성 시각. 없으면 null
 * @param oldestOpenAgeSeconds 위 요청이 기다린 시간. 없으면 null
 * @param attentionCount 운영자가 확인할 UNKNOWN과 FAILED 건수의 합
 */
public record DmMailOutboxSummaryResponse(
        Map<DmMailStatus, Long> counts,
        LocalDateTime oldestOpenCreatedAt,
        Long oldestOpenAgeSeconds,
        long attentionCount,
        LocalDateTime checkedAt) {}
