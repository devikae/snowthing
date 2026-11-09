package com.ikae.snowthing.domain.carpool.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ikae.snowthing.domain.carpool.entity.CarpoolReview;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewRating;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewTag;

/** 태그는 GOOD일 때만 고를 수 있다. comment는 대상 본인에게만 보인다. */
public record CarpoolReviewCreateRequest(
        @NotBlank @Size(max = 64) String revieweePublicId,
        @NotNull CarpoolReviewRating rating,
        @Size(max = 5) List<@NotNull CarpoolReviewTag> tags,
        @Size(max = CarpoolReview.COMMENT_MAX_LENGTH) String comment) {}
