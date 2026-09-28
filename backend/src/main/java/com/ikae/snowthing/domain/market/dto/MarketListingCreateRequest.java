package com.ikae.snowthing.domain.market.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;

public record MarketListingCreateRequest(
        @NotBlank String categoryCode,
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        @NotNull ProductCondition productCondition,
        @NotNull TransactionMethod transactionMethod,
        Long price,
        boolean negotiable,
        boolean free,
        @NotBlank @Size(max = 30) String contact,
        @Size(max = 5) List<String> imageUrls) {

    public MarketListingCreateRequest {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }
}
