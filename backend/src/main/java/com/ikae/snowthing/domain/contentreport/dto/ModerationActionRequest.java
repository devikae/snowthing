package com.ikae.snowthing.domain.contentreport.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ikae.snowthing.domain.contentreport.entity.ModerationActionType;

/** {@code holdDays}는 임시조치에만 쓴다. 법정 상한이 30일이다. */
public record ModerationActionRequest(
        @NotNull ModerationActionType action,
        @Min(1) @Max(30) Integer holdDays,
        @Size(max = 500) String memo) {}
