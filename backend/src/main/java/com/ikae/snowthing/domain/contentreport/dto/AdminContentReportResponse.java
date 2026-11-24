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
        String targetContent,
        String postPublicId,
        String postCategoryCode,
        ReportReason reason,
        String detail,
        String reporterNickname,
        ReportStatus status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        // 쪽지 신고일 때만 채운다. 탈퇴한 회원이면 publicId는 null이다 (IKA-123).
        String senderPublicId,
        String senderNickname,
        LocalDateTime messageSentAt) {

    public enum TargetState {
        NORMAL,
        HELD,
        HIDDEN,
        DELETED
    }
}
