package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

/** 관리자 조치 기록 항목. 조치한 관리자는 닉네임과 공개 ID로만 보여 준다. */
public record AdminActionLogItem(
        Long id,
        String action,
        String targetType,
        String targetId,
        String reason,
        String actorNickname,
        String actorPublicId,
        LocalDateTime createdAt) {}
