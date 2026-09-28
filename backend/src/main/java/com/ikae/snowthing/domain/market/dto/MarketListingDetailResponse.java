package com.ikae.snowthing.domain.market.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TradeStatus;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;

public record MarketListingDetailResponse(
        String publicId,
        String title,
        String content,
        MarketListingSummaryResponse.Category category,
        ProductCondition productCondition,
        TransactionMethod transactionMethod,
        TradeStatus tradeStatus,
        long price,
        boolean negotiable,
        boolean free,
        String contact,
        MarketListingSummaryResponse.Seller seller,
        List<String> images,
        int viewCount,
        int commentCount,
        long version,
        boolean canEdit,
        boolean canDelete,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public MarketListingDetailResponse {
        images = List.copyOf(images);
    }
}
