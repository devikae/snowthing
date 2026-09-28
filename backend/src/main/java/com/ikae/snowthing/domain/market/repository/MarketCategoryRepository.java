package com.ikae.snowthing.domain.market.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.market.entity.MarketCategory;

public interface MarketCategoryRepository extends JpaRepository<MarketCategory, Long> {

    Optional<MarketCategory> findByCode(String code);

    List<MarketCategory> findAllByActiveTrueOrderBySortOrderAscIdAsc();
}
