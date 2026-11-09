package com.ikae.snowthing.domain.carpool.dto;

import java.util.List;

import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewTag;

/** 누구나 보는 회원별 카풀 요약. 아쉬웠어요 개수와 비율은 넣지 않는다. */
public record CarpoolReviewSummaryResponse(long rideCount, long goodCount, List<TagCount> topTags) {

    public record TagCount(CarpoolReviewTag tag, long count) {}
}
