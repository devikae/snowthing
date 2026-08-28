package com.ikae.snowthing.domain.member.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.member.entity.MemberRidingStyle;

public interface MemberRidingStyleRepository extends JpaRepository<MemberRidingStyle, Long> {

    @Query(
            "SELECT mrs FROM MemberRidingStyle mrs JOIN FETCH mrs.ridingStyle WHERE mrs.member.id = :memberId")
    List<MemberRidingStyle> findAllByMemberIdWithRidingStyle(@Param("memberId") Long memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM MemberRidingStyle mrs WHERE mrs.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
