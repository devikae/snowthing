package com.ikae.snowthing.domain.market.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ikae.snowthing.domain.market.entity.MarketListing;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TradeStatus;

public interface MarketListingRepositoryCustom {

    Page<MarketListing> search(
            String keyword,
            String categoryCode,
            ProductCondition productCondition,
            TradeStatus tradeStatus,
            Pageable pageable);

    List<MarketListing> findLatestVisible(int limit);
}
