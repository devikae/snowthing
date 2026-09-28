package com.ikae.snowthing.domain.market.dto;

import jakarta.validation.constraints.NotNull;

import com.ikae.snowthing.domain.post.entity.PostStatus;

public record MarketModerationStatusUpdateRequest(@NotNull PostStatus moderationStatus) {}
