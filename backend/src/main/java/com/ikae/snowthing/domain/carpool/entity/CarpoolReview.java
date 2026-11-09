package com.ikae.snowthing.domain.carpool.entity;

import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.*;

import org.hibernate.annotations.BatchSize;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 카풀 후기 (043). 한 카풀에서 한 사람에게 한 번만 쓰고 고치지 않는다. */
@Entity
@Table(
        name = "carpool_review",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_carpool_review_post_reviewer_reviewee",
                        columnNames = {"post_id", "reviewer_id", "reviewee_id"}),
        indexes = {
            @Index(name = "idx_carpool_review_reviewee_rating", columnList = "reviewee_id, rating"),
            @Index(name = "idx_carpool_review_reviewer", columnList = "reviewer_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CarpoolReview extends BaseTimeEntity {

    public static final int COMMENT_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "carpool_review_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private CarpoolDetail carpool;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private Member reviewer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private Member reviewee;

    @Enumerated(EnumType.STRING)
    @Column(name = "rating", nullable = false, length = 10)
    private CarpoolReviewRating rating;

    @Column(name = "comment", length = COMMENT_MAX_LENGTH)
    private String comment;

    // 받은 후기 목록(최대 20개)에서 태그를 한 번에 읽는다.
    @BatchSize(size = 20)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "carpool_review_tag",
            joinColumns = @JoinColumn(name = "carpool_review_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 30)
    private Set<CarpoolReviewTag> tags = EnumSet.noneOf(CarpoolReviewTag.class);

    public CarpoolReview(
            CarpoolDetail carpool,
            Member reviewer,
            Member reviewee,
            CarpoolReviewRating rating,
            Set<CarpoolReviewTag> tags,
            String comment) {
        this.carpool = carpool;
        this.reviewer = reviewer;
        this.reviewee = reviewee;
        this.rating = rating;
        this.tags = tags.isEmpty() ? EnumSet.noneOf(CarpoolReviewTag.class) : EnumSet.copyOf(tags);
        this.comment = comment;
    }
}
