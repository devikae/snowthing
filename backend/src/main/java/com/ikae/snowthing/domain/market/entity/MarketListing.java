package com.ikae.snowthing.domain.market.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.Check;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.global.common.BaseTimeEntity;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_listing")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Check(
        constraints =
                "(is_free = true AND price = 0 AND is_negotiable = false) OR (is_free = false AND price BETWEEN 1 AND 20000000)")
public class MarketListing extends BaseTimeEntity {

    public static final long MAX_PRICE = 20_000_000L;
    public static final int MAX_CONTACT_LENGTH = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "market_listing_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_category_id", nullable = false)
    private MarketCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_condition", nullable = false, length = 20)
    private ProductCondition productCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_method", nullable = false, length = 20)
    private TransactionMethod transactionMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_status", nullable = false, length = 20)
    private TradeStatus tradeStatus;

    @Column(nullable = false)
    private long price;

    @Column(name = "is_negotiable", nullable = false)
    private boolean negotiable;

    @Column(name = "is_free", nullable = false)
    private boolean free;

    @Column(nullable = false, length = MAX_CONTACT_LENGTH)
    private String contact;

    @Version
    @Column(nullable = false)
    private long version;

    @Builder
    public MarketListing(
            Post post,
            MarketCategory category,
            ProductCondition productCondition,
            TransactionMethod transactionMethod,
            Long price,
            boolean negotiable,
            boolean free,
            String contact) {
        this.post = require(post);
        this.category = require(category);
        this.productCondition = require(productCondition);
        this.transactionMethod = require(transactionMethod);
        this.tradeStatus = TradeStatus.ON_SALE;
        applyPrice(price, negotiable, free);
        this.contact = normalizeContact(contact);
    }

    public void update(
            MarketCategory category,
            ProductCondition productCondition,
            TransactionMethod transactionMethod,
            Long price,
            boolean negotiable,
            boolean free,
            String contact) {
        this.category = require(category);
        this.productCondition = require(productCondition);
        this.transactionMethod = require(transactionMethod);
        applyPrice(price, negotiable, free);
        this.contact = normalizeContact(contact);
    }

    public boolean changeTradeStatus(TradeStatus newStatus) {
        TradeStatus resolvedStatus = require(newStatus);
        if (this.tradeStatus == resolvedStatus) {
            return false;
        }
        this.tradeStatus = resolvedStatus;
        return true;
    }

    private void applyPrice(Long requestedPrice, boolean negotiable, boolean free) {
        if (free) {
            if (requestedPrice != null && requestedPrice != 0L) {
                throw new CustomException(ErrorCode.MARKET_INVALID_PRICE);
            }
            this.price = 0L;
            this.negotiable = false;
            this.free = true;
            return;
        }
        if (requestedPrice == null || requestedPrice < 1L || requestedPrice > MAX_PRICE) {
            throw new CustomException(ErrorCode.MARKET_INVALID_PRICE);
        }
        this.price = requestedPrice;
        this.negotiable = negotiable;
        this.free = false;
    }

    private String normalizeContact(String value) {
        if (value == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalized = value.trim();
        if (normalized.isEmpty()
                || normalized.length() > MAX_CONTACT_LENGTH
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private <T> T require(T value) {
        if (value == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return value;
    }
}
