package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

/** 관리자 설질 제보 목록 항목. */
public record AdminResortReportItem(
        Long reportId,
        String resortName,
        String content,
        String writerNickname,
        String status,
        LocalDateTime createdAt) {}
