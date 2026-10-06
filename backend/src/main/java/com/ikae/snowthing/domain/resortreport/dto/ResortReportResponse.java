package com.ikae.snowthing.domain.resortreport.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.resortreport.entity.ResortReport;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResortReportResponse {

    private final Long reportId;
    private final Long resortId;
    private final String resortName;
    private final String resortCode;
    private final String authorNickname;
    private final String content;
    private final LocalDateTime createdAt;

    public static ResortReportResponse from(ResortReport report) {
        return ResortReportResponse.builder()
                .reportId(report.getId())
                .resortId(report.getResort().getId())
                .resortName(report.getResort().getName())
                .resortCode(report.getResort().getCode())
                .authorNickname(report.getAuthor().getNickname())
                .content(report.getContent())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
