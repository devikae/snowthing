package com.ikae.snowthing.domain.market.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TradeStatus;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;

public record MarketListingSummaryResponse(
        String publicId,
        String title,
        Category category,
        ProductCondition productCondition,
        TransactionMethod transactionMethod,
        TradeStatus tradeStatus,
        long price,
        boolean negotiable,
        boolean free,
        String thumbnailImageUrl,
        Seller seller,
        int viewCount,
        int commentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public record Category(String code, String name) {}

    public record Seller(String publicId, String nickname, String profileImageUrl) {}
}
