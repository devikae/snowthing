package com.ikae.snowthing.domain.contentreport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ikae.snowthing.domain.contentreport.entity.ReportReason;
import com.ikae.snowthing.domain.contentreport.entity.ReportTargetType;

/** {@code targetId}는 게시글이면 publicId, 댓글이면 댓글 ID다. */
public record ContentReportCreateRequest(
        @NotNull ReportTargetType targetType,
        @NotBlank @Size(max = 36) String targetId,
        @NotNull ReportReason reason,
        @Size(max = 500) String detail) {}
