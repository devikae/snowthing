package com.ikae.snowthing.domain.admin.dto;

import java.util.List;

public record AdminDashboardStatsResponse(
        int todayTotalVisitors,
        int todayMemberVisitors,
        int todayGuestVisitors,
        int todayPvCount,
        long totalMembers,
        long todayNewMembers,
        boolean anonymousChatEnabled,
        List<DailyVisitorStatDto> visitorTrends) {

    public AdminDashboardStatsResponse {
        visitorTrends = visitorTrends == null ? List.of() : List.copyOf(visitorTrends);
    }
}
