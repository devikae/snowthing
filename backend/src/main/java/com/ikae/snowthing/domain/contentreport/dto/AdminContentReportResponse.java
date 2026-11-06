package com.ikae.snowthing.domain.contentreport.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.contentreport.entity.ReportReason;
import com.ikae.snowthing.domain.contentreport.entity.ReportStatus;
import com.ikae.snowthing.domain.contentreport.entity.ReportTargetType;

public record AdminContentReportResponse(
        Long reportId,
        ReportTargetType targetType,
        TargetState targetState,
        String targetPreview,
        String postPublicId,
        String postCategoryCode,
        ReportReason reason,
        String detail,
        String reporterNickname,
        ReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt) {

    public enum TargetState {
        NORMAL,
        HELD,
        DELETED
    }
}
