package com.ikae.snowthing.domain.carpool.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.carpool.entity.CarpoolRider;
import com.ikae.snowthing.domain.carpool.entity.CarpoolRiderStatus;

public interface CarpoolRiderRepository extends JpaRepository<CarpoolRider, Long> {

    Optional<CarpoolRider> findByCarpool_IdAndMember_Id(Long carpoolId, Long memberId);

    boolean existsByCarpool_IdAndMember_Id(Long carpoolId, Long memberId);

    /**
     * 확정과 거절에서 쓰는 잠금 읽기. REPEATABLE READ에서 일반 SELECT는 트랜잭션의 첫 읽기 시점 스냅샷을 보므로, 카풀 행 잠금을 기다리는 동안 다른
     * 요청이 커밋한 확정을 놓친다. 잠금 읽기는 최신 커밋을 본다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CarpoolRider r where r.carpool.id = :carpoolId and r.status = :status")
    List<CarpoolRider> findForUpdateByStatus(
            @Param("carpoolId") Long carpoolId, @Param("status") CarpoolRiderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select r from CarpoolRider r where r.carpool.id = :carpoolId and r.member.id = :memberId")
    Optional<CarpoolRider> findForUpdate(
            @Param("carpoolId") Long carpoolId, @Param("memberId") Long memberId);

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
