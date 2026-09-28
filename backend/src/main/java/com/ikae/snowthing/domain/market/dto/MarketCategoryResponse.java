package com.ikae.snowthing.domain.market.dto;

import com.ikae.snowthing.domain.market.entity.MarketCategory;

public record MarketCategoryResponse(String code, String name, int sortOrder) {

    public static MarketCategoryResponse from(MarketCategory category) {
        return new MarketCategoryResponse(
                category.getCode(), category.getName(), category.getSortOrder());
    }
}
