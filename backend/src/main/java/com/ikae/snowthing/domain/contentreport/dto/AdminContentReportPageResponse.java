package com.ikae.snowthing.domain.contentreport.dto;

import java.util.List;

public record AdminContentReportPageResponse(
        List<AdminContentReportResponse> reports, int page, int totalPages, long totalElements) {

    public AdminContentReportPageResponse {
        reports = List.copyOf(reports);
    }
}
