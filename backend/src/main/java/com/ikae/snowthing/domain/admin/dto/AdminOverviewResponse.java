package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDate;
import java.util.List;

/** 관리자 대시보드 (IKA-78). 날짜는 KST 기준이고 기간 안의 빈 날도 0으로 채운다. 마지막 항목이 오늘이다. */
public record AdminOverviewResponse(
        int days,
        DailyCount today,
        List<DailyCount> series,
        long pendingReports,
        long unansweredInquiries) {

    public record DailyCount(LocalDate date, long signups, long posts, long comments) {}
}
