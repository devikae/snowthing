package com.ikae.snowthing.domain.seasonlog.dto;

import java.time.LocalDate;

import com.ikae.snowthing.domain.seasonlog.entity.SeasonVisit;

public record SeasonVisitResponse(LocalDate visitDate, Long resortId, String resortName) {
    public static SeasonVisitResponse from(SeasonVisit visit) {
        return new SeasonVisitResponse(
                visit.getVisitDate(), visit.getResort().getId(), visit.getResort().getName());
    }
}
