package com.ikae.snowthing.domain.carpool.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.carpool.entity.CarpoolRider;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRiderStatus;

public interface CarpoolRiderRepository extends JpaRepository<CarpoolRider, Long> {

    Optional<CarpoolRider> findByCarpool_IdAndMember_Id(Long carpoolId, Long memberId);

    boolean existsByCarpool_IdAndMember_Id(Long carpoolId, Long memberId);

    long countByCarpool_IdAndStatus(Long carpoolId, CarpoolRiderStatus status);

    @Query(
            """
            select r from CarpoolRider r join fetch r.member
            where r.carpool.id = :carpoolId
            order by r.createdAt asc, r.id asc
            """)
    List<CarpoolRider> findAllWithMember(@Param("carpoolId") Long carpoolId);

    /** 탑승자로 확정된 카풀 수. 삭제되었거나 숨김인 카풀은 세지 않는다. */
    @Query(
            """
            select count(r) from CarpoolRider r join r.carpool d join d.post p
            where r.member.id = :memberId
              and r.status = com.ikae.snowthing.domain.carpool.entity.CarpoolRiderStatus.CONFIRMED
              and p.isDeleted = false
              and p.status = com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL
            """)
    long countConfirmedRides(@Param("memberId") Long memberId);

    /** 운전한 카풀 가운데 확정된 탑승자가 한 명 이상인 카풀 수. */
    @Query(
            """
            select count(d) from CarpoolDetail d join d.post p
            where p.member.id = :memberId
              and p.isDeleted = false
              and p.status = com.ikae.snowthing.domain.post.entity.PostStatus.NORMAL
              and exists (
                select 1 from CarpoolRider r
                where r.carpool = d
                  and r.status = com.ikae.snowthing.domain.carpool.entity.CarpoolRiderStatus.CONFIRMED)
            """)
    long countDrivenRides(@Param("memberId") Long memberId);
}
