package com.ikae.snowthing.domain.market.repository;

import static com.ikae.snowthing.domain.market.entity.QMarketCategory.marketCategory;
import static com.ikae.snowthing.domain.market.entity.QMarketListing.marketListing;
import static com.ikae.snowthing.domain.member.entity.QMember.member;
import static com.ikae.snowthing.domain.post.entity.QPost.post;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.ikae.snowthing.domain.market.entity.MarketListing;
import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TradeStatus;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class MarketListingRepositoryCustomImpl implements MarketListingRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<MarketListing> search(
            String keyword,
            String categoryCode,
            ProductCondition productCondition,
            TradeStatus tradeStatus,
            Pageable pageable) {
        BooleanExpression visibility =
                post.status.eq(PostStatus.NORMAL).and(post.isDeleted.isFalse());
        BooleanBuilder filters = new BooleanBuilder();
        filters.and(keywordContains(keyword));
        filters.and(categoryCodeEq(categoryCode));
        filters.and(productConditionEq(productCondition));
        filters.and(tradeStatusCondition(keyword, tradeStatus));

        Long total =
                queryFactory
                        .select(marketListing.count())
                        .from(marketListing)
                        .join(marketListing.post, post)
                        .join(marketListing.category, marketCategory)
                        .where(visibility, filters)
                        .fetchOne();

        List<MarketListing> content =
                queryFactory
                        .selectFrom(marketListing)
                        .join(marketListing.post, post)
                        .fetchJoin()
                        .join(post.member, member)
                        .fetchJoin()
                        .join(marketListing.category, marketCategory)
                        .fetchJoin()
                        .where(visibility, filters)
                        .orderBy(marketListing.createdAt.desc(), marketListing.id.desc())
                        .offset(pageable.getOffset())
                        .limit(pageable.getPageSize())
                        .fetch();

        return new PageImpl<>(content, pageable, total == null ? 0L : total);
    }

    @Override
    public List<MarketListing> findLatestVisible(int limit) {
        return queryFactory
                .selectFrom(marketListing)
                .join(marketListing.post, post)
                .fetchJoin()
                .where(
                        post.status.eq(PostStatus.NORMAL),
                        post.isDeleted.isFalse(),
                        marketListing.tradeStatus.in(TradeStatus.ON_SALE, TradeStatus.RESERVED))
                .orderBy(marketListing.createdAt.desc(), marketListing.id.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanExpression keywordContains(String keyword) {
        return StringUtils.hasText(keyword) ? post.title.containsIgnoreCase(keyword.trim()) : null;
    }

    private BooleanExpression categoryCodeEq(String categoryCode) {
        return StringUtils.hasText(categoryCode)
                ? marketCategory.code.equalsIgnoreCase(categoryCode.trim())
                : null;
    }

    private BooleanExpression productConditionEq(ProductCondition productCondition) {
        return productCondition == null
                ? null
                : marketListing.productCondition.eq(productCondition);
    }

    private BooleanExpression tradeStatusCondition(String keyword, TradeStatus tradeStatus) {
        if (tradeStatus != null) {
            return marketListing.tradeStatus.eq(tradeStatus);
        }
        if (StringUtils.hasText(keyword)) {
            return null;
        }
        return marketListing.tradeStatus.in(TradeStatus.ON_SALE, TradeStatus.RESERVED);
    }
}
