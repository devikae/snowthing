package com.ikae.snowthing.domain.market.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.market.entity.ProductCondition;
import com.ikae.snowthing.domain.market.entity.TransactionMethod;

class MarketDtoImmutabilityTest {

    @Test
    void 생성요청은_이미지목록을_방어적으로_복사한다() {
        List<String> original = new ArrayList<>(List.of("public/posts/originals/one.jpg"));
        MarketListingCreateRequest request =
                new MarketListingCreateRequest(
                        "SNOWBOARD",
                        "판매글",
                        "본문",
                        ProductCondition.GOOD,
                        TransactionMethod.BOTH,
                        10_000L,
                        false,
                        false,
                        "contact",
                        original);

        original.add("public/posts/originals/two.jpg");

        assertThat(request.imageUrls()).containsExactly("public/posts/originals/one.jpg");
        assertThatThrownBy(() -> request.imageUrls().add("public/posts/originals/three.jpg"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 수정요청의_null_이미지목록은_빈_불변목록이_된다() {
        MarketListingUpdateRequest request =
                new MarketListingUpdateRequest(
                        0L,
                        "SNOWBOARD",
                        "판매글",
                        "본문",
                        ProductCondition.GOOD,
                        TransactionMethod.BOTH,
                        10_000L,
                        false,
                        false,
                        "contact",
                        null);

        assertThat(request.imageUrls()).isEmpty();
        assertThatThrownBy(() -> request.imageUrls().add("public/posts/originals/one.jpg"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
