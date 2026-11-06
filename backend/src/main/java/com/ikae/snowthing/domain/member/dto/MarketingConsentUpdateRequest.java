package com.ikae.snowthing.domain.member.dto;

import jakarta.validation.constraints.NotNull;

public record MarketingConsentUpdateRequest(@NotNull Boolean agreed) {}
