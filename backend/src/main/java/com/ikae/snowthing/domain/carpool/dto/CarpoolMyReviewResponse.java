package com.ikae.snowthing.domain.carpool.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ikae.snowthing.domain.carpool.entity.CarpoolReview;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewRating;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewTag;

/** 본인만 보는 요약과 받은 후기. 받은 후기에는 작성자를 넣지 않는다. */
public record CarpoolMyReviewResponse(
        long rideCount,
        long reviewCount,
        long goodCount,
        List<CarpoolReviewSummaryResponse.TagCount> topTags,
        List<Received> received) {

    public record Received(
            CarpoolReviewRating rating,
            List<CarpoolReviewTag> tags,
            String comment,
            String carpoolPublicId,
            String carpoolTitle,
            LocalDateTime createdAt) {

        public static Received from(CarpoolReview review) {
            return new Received(
                    review.getRating(),
                    review.getTags().stream().sorted().toList(),
                    review.getComment(),
                    review.getCarpool().getPost().getPublicId(),
                    review.getCarpool().getPost().getTitle(),
                    review.getCreatedAt());
        }
    }
}
