package com.ikae.snowthing.domain.seasonlog.dto;

import java.time.LocalDate;

import com.ikae.snowthing.domain.seasonlog.model.Season;

public record SeasonResponse(int startYear, String label, LocalDate startDate, LocalDate endDate) {
    public static SeasonResponse from(Season season) {
        return new SeasonResponse(
                season.startYear(), season.label(), season.startDate(), season.endDate());
    }
}
