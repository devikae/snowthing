package com.ikae.snowthing.domain.seasonlog.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.seasonlog.entity.SeasonVisit;

public interface SeasonVisitRepository extends JpaRepository<SeasonVisit, Long> {

    /** uk_season_visit_member_date(member_id, visit_date) 앞부분으로 범위를 찾는다. */
    @Query(
            "SELECT v FROM SeasonVisit v JOIN FETCH v.resort "
                    + "WHERE v.member.id = :memberId "
                    + "AND v.visitDate BETWEEN :start AND :end "
                    + "ORDER BY v.visitDate ASC, v.id ASC")
    List<SeasonVisit> findInRange(
            @Param("memberId") Long memberId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    boolean existsByMember_IdAndVisitDate(Long memberId, LocalDate visitDate);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SeasonVisit v WHERE v.member.id = :memberId AND v.visitDate = :visitDate")
    int deleteByMemberAndDate(
            @Param("memberId") Long memberId, @Param("visitDate") LocalDate visitDate);
}
