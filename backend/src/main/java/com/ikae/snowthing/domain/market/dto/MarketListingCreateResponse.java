package com.ikae.snowthing.domain.market.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.market.entity.MarketListing;
import com.ikae.snowthing.domain.market.entity.TradeStatus;
import com.ikae.snowthing.domain.post.entity.PostStatus;

public record MarketListingCreateResponse(
        String publicId,
        TradeStatus tradeStatus,
        PostStatus moderationStatus,
        long version,
        LocalDateTime createdAt) {

    public static MarketListingCreateResponse from(MarketListing listing) {
        return new MarketListingCreateResponse(
                listing.getPost().getPublicId(),
                listing.getTradeStatus(),
                listing.getPost().getStatus(),
                listing.getVersion(),
                listing.getCreatedAt());
    }
}
