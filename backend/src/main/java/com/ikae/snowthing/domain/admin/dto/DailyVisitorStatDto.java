package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDate;

public record DailyVisitorStatDto(
        LocalDate date, int totalVisitors, int memberVisitors, int guestVisitors, int pvCount) {}
