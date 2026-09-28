package com.ikae.snowthing.domain.market.repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.market.entity.MarketListing;

public interface MarketListingRepository
        extends JpaRepository<MarketListing, Long>, MarketListingRepositoryCustom {

    @Query(
            "SELECT ml FROM MarketListing ml JOIN FETCH ml.post p JOIN FETCH p.member JOIN FETCH ml.category WHERE p.publicId = :publicId")
    Optional<MarketListing> findDetailByPostPublicId(@Param("publicId") String publicId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query(
            "SELECT ml FROM MarketListing ml JOIN FETCH ml.post p JOIN FETCH p.member JOIN FETCH ml.category WHERE p.publicId = :publicId")
    Optional<MarketListing> findForUpdateByPostPublicId(@Param("publicId") String publicId);

    @Lock(LockModeType.OPTIMISTIC)
    @Query(
            "SELECT ml FROM MarketListing ml JOIN FETCH ml.post p JOIN FETCH p.member JOIN FETCH ml.category WHERE p.publicId = :publicId")
    Optional<MarketListing> findForTradeStatusUpdateByPostPublicId(
            @Param("publicId") String publicId);

    boolean existsByPostId(Long postId);
}
