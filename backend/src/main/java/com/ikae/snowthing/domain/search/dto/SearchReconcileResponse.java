package com.ikae.snowthing.domain.search.dto;

import java.time.LocalDateTime;

/** 대조 결과 outbox에 넣은 이벤트 수. 이미 PENDING 이벤트가 있는 원문은 세지 않는다. */
public record SearchReconcileResponse(
        int postUpserts,
        int postDeletes,
        int commentUpserts,
        int commentDeletes,
        LocalDateTime enqueuedAt) {}
