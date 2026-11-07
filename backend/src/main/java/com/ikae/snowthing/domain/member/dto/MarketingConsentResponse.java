package com.ikae.snowthing.domain.member.dto;

import java.time.LocalDateTime;

public record MarketingConsentResponse(boolean agreed, LocalDateTime changedAt) {}
