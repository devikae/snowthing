package com.ikae.snowthing.domain.admin.dto;

public record AdminDashboardStatsResponse(
        long totalMembers, long todayNewMembers, boolean anonymousChatEnabled) {}
