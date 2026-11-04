package com.ikae.snowthing.global.idempotency;

/** 멱등 처리하는 작성 API 종류. 같은 키라도 대상 종류가 다르면 서로 다른 요청으로 본다. */
public enum IdempotencyTarget {
    POST,
    COMMENT,
    RESORT_REPORT,
    CARPOOL,
    MARKET_LISTING
}
