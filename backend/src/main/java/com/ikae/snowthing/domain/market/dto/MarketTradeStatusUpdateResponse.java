package com.ikae.snowthing.domain.market.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.market.entity.TradeStatus;

public record MarketTradeStatusUpdateResponse(
        String publicId, TradeStatus tradeStatus, long version, LocalDateTime updatedAt) {}
