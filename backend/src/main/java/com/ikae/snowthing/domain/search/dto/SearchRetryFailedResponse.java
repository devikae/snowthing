package com.ikae.snowthing.domain.search.dto;

/** PENDING으로 되돌린 FAILED 이벤트 수. */
public record SearchRetryFailedResponse(int retriedCount) {}
