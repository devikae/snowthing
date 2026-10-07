package com.ikae.snowthing.domain.resortreport.dto;

import jakarta.validation.constraints.NotNull;

import com.ikae.snowthing.domain.resortreport.entity.ResortReportStatus;

public record ResortReportModerationStatusUpdateRequest(
        @NotNull ResortReportStatus moderationStatus) {}
