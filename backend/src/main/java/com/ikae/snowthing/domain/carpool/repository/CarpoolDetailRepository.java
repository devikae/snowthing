package com.ikae.snowthing.domain.carpool.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.carpool.entity.CarpoolDetail;
import com.ikae.snowthing.domain.post.entity.PostStatus;

public interface CarpoolDetailRepository extends JpaRepository<CarpoolDetail, Long> {

    Optional<CarpoolDetail> findByPostPublicId(String publicId);

    /** 탑승 확정이 정원을 넘지 않게 카풀 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from CarpoolDetail d where d.id = :postId")
    Optional<CarpoolDetail> findByIdForUpdate(@Param("postId") Long postId);

    @EntityGraph(attributePaths = {"post", "post.member", "destinationResort"})
    Page<CarpoolDetail> findByPostStatusAndPostIsDeletedFalseAndDepartureAtGreaterThanEqual(
            PostStatus status, LocalDateTime departureAt, Pageable pageable);

    // 제목, 본문, 출발지, 집결지, 도착 리조트에서 찾는다. 본문은 LONGTEXT라 문자열로 변환한다.
    @EntityGraph(attributePaths = {"post", "post.member", "destinationResort"})
    @Query(
            """
            select d from CarpoolDetail d
            where d.post.status = :status
              and d.post.isDeleted = false
              and d.departureAt >= :departureAt
              and (lower(d.post.title) like :pattern escape '!'
                or lower(cast(d.post.content as string)) like :pattern escape '!'
                or lower(d.departureRegion) like :pattern escape '!'
                or lower(d.meetingPlace) like :pattern escape '!'
                or lower(d.destinationResort.name) like :pattern escape '!')
            """)
    Page<CarpoolDetail> searchVisible(
            @Param("status") PostStatus status,
            @Param("departureAt") LocalDateTime departureAt,
            @Param("pattern") String pattern,
            Pageable pageable);
}
