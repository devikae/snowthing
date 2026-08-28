package com.ikae.snowthing.domain.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.member.entity.MemberResort;

public interface MemberResortRepository extends JpaRepository<MemberResort, Long> {

    @Query("SELECT mr FROM MemberResort mr JOIN FETCH mr.resort WHERE mr.member.id = :memberId")
    List<MemberResort> findAllByMemberIdWithResort(@Param("memberId") Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MemberResort mr WHERE mr.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
