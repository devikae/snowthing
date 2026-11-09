package com.ikae.snowthing.domain.seasonlog.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record SeasonVisitCreateRequest(
        @NotNull(message = "다녀온 날짜를 골라 주세요.") LocalDate visitDate,
        @NotNull(message = "리조트를 골라 주세요.") Long resortId) {}
