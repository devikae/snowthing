package com.ikae.snowthing.domain.resortreport.dto;

import java.time.OffsetDateTime;
import java.time.ZoneId;

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
    private final OffsetDateTime createdAt;
    private final boolean canDelete;

    public static ResortReportResponse from(ResortReport report, boolean canDelete) {
        return ResortReportResponse.builder()
                .reportId(report.getId())
                .resortId(report.getResort().getId())
                .resortName(report.getResort().getName())
                .resortCode(report.getResort().getCode())
                .authorNickname(report.getAuthor().getNickname())
                .content(report.getContent())
                .createdAt(report.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toOffsetDateTime())
                .canDelete(canDelete)
                .build();
    }
}
