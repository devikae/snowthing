package com.ikae.snowthing.domain.market.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class MarketListingTest {

    @Test
    void 무료_나눔은_가격과_가격협의를_정규화한다() {
        MarketListing listing = listing(0L, true, true, " 010-1234-5678 ");

        assertThat(listing.getPrice()).isZero();
        assertThat(listing.isNegotiable()).isFalse();
        assertThat(listing.isFree()).isTrue();
        assertThat(listing.getContact()).isEqualTo("010-1234-5678");
    }

    @Test
    void 유료_가격은_이천만원까지_허용한다() {
        MarketListing listing = listing(20_000_000L, true, false, "snowthing_id");

        assertThat(listing.getPrice()).isEqualTo(20_000_000L);
    }

    @Test
    void 유료_가격이_이천만원을_넘으면_거부한다() {
        assertThatThrownBy(() -> listing(20_000_001L, false, false, "snowthing_id"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MARKET_INVALID_PRICE);
    }

    @Test
    void 연락처가_삼십자를_넘으면_거부한다() {
        assertThatThrownBy(() -> listing(1L, false, false, "a".repeat(31)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 연락처에_제어문자가_있으면_거부한다() {
        assertThatThrownBy(() -> listing(1L, false, false, "010-1234\n5678"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void 같은_거래상태로_변경하면_버전을_증가시킬_변경이_없다() {
        MarketListing listing = listing(1L, false, false, "snowthing_id");

        assertThat(listing.changeTradeStatus(TradeStatus.ON_SALE)).isFalse();
        assertThat(listing.getTradeStatus()).isEqualTo(TradeStatus.ON_SALE);
    }

    private MarketListing listing(Long price, boolean negotiable, boolean free, String contact) {
        Member member = Member.builder().email("seller@test.com").nickname("판매자").build();
        PostCategory postCategory = PostCategory.builder().code("MARKET").name("중고장터").build();
        Post post =
                Post.builder()
                        .member(member)
                        .category(postCategory)
                        .title("판매글")
                        .content("본문")
                        .writerIp("127.0.0.1")
                        .build();
        MarketCategory marketCategory =
                MarketCategory.builder()
                        .code("SNOWBOARD")
                        .name("스노보드")
                        .sortOrder(1)
                        .active(true)
                        .build();
        return MarketListing.builder()
                .post(post)
                .category(marketCategory)
                .productCondition(ProductCondition.GOOD)
                .transactionMethod(TransactionMethod.BOTH)
                .price(price)
                .negotiable(negotiable)
                .free(free)
                .contact(contact)
                .build();
    }
}
