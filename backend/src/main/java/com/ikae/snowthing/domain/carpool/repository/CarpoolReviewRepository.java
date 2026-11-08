package com.ikae.snowthing.domain.carpool.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.carpool.entity.CarpoolReview;
import com.ikae.snowthing.domain.carpool.entity.CarpoolReviewRating;

/** 집계는 모두 삭제되었거나 숨김인 카풀을 뺀다. 관리자가 가짜 카풀을 지우면 그 후기도 빠진다. */
public interface CarpoolReviewRepository extends JpaRepository<CarpoolReview, Long> {

    boolean existsByCarpool_IdAndReviewer_IdAndReviewee_Id(
            Long carpoolId, Long reviewerId, Long revieweeId);

    @Query(
            "select r.reviewee.id from CarpoolReview r"
                    + " where r.carpool.id = :carpoolId and r.reviewer.id = :reviewerId")
    List<Long> findRevieweeIds(
            @Param("carpoolId") Long carpoolId, @Param("reviewerId") Long reviewerId);

    @Query(
            """
            select count(r) from CarpoolReview r join r.carpool d join d.post p
            where r.reviewee.id = :memberId
              and (:rating is null or r.rating = :rating)
              and p.isDeleted = false
              and p.status = com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL
            """)
    long countReceived(
            @Param("memberId") Long memberId, @Param("rating") CarpoolReviewRating rating);

    /** 좋았어요 후기의 태그별 개수. [태그, 개수] 순서다. */
    @Query(
            """
            select t, count(r) from CarpoolReview r join r.tags t join r.carpool d join d.post p
            where r.reviewee.id = :memberId
              and r.rating = com.ikae.snowthing.domain.carpool.entity.CarpoolReviewRating.GOOD
              and p.isDeleted = false
              and p.status = com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL
            group by t
            order by count(r) desc, t asc
            """)
    List<Object[]> countGoodTags(@Param("memberId") Long memberId, Pageable pageable);

    @Query(
            """
            select r from CarpoolReview r join fetch r.carpool d join fetch d.post p
            where r.reviewee.id = :memberId
              and p.isDeleted = false
              and p.status = com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL
            order by r.createdAt desc, r.id desc
            """)
    List<CarpoolReview> findReceived(@Param("memberId") Long memberId, Pageable pageable);
}
