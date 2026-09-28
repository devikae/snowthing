package com.ikae.snowthing.domain.market.dto;

import java.util.List;

public record MarketCategoryListResponse(List<MarketCategoryResponse> categories) {
    public MarketCategoryListResponse {
        categories = List.copyOf(categories);
    }
}
