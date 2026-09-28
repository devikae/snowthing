package com.ikae.snowthing.domain.market.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import com.ikae.snowthing.domain.market.entity.TradeStatus;

public record MarketTradeStatusUpdateRequest(
        @NotNull TradeStatus tradeStatus, @NotNull @PositiveOrZero Long version) {}
