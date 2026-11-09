package com.ikae.snowthing.domain.market.dto;

import com.ikae.snowthing.domain.market.entity.TradeStatus;

/** 링크 공유 미리보기용. 로그인 없이 공개되므로 제목, 가격, 대표 사진만 담는다(본문, 연락처, 판매자 제외). */
public record MarketSharePreviewResponse(
        String title,
        long price,
        boolean negotiable,
        boolean free,
        TradeStatus tradeStatus,
        String imageUrl) {}
