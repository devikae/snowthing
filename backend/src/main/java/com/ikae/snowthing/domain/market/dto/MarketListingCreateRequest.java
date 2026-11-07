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
        @NotBlank @Size(max = 100000, message = "본문은 최대 100,000자까지 입력 가능합니다.") String content,
        @NotNull ProductCondition productCondition,
        @NotNull TransactionMethod transactionMethod,
        Long price,
        boolean negotiable,
        boolean free,
        @NotBlank @Size(max = 30) String contact,
        @Size(max = 10) List<String> imageUrls) {

    public MarketListingCreateRequest {
        imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
    }
}
