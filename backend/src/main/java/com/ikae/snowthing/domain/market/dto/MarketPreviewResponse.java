package com.ikae.snowthing.domain.market.dto;

import java.util.List;

public record MarketPreviewResponse(List<Item> items) {
    public MarketPreviewResponse {
        items = List.copyOf(items);
    }

    public record Item(String publicId, String thumbnailImageUrl) {}
}
