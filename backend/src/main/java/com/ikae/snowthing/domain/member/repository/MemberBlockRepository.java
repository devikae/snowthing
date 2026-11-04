package com.ikae.snowthing.domain.member.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.member.entity.MemberBlock;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, Long> {

    /** 이미 차단한 회원이면 아무것도 바꾸지 않는다. UNIQUE 위반 예외 없이 멱등하게 처리한다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "INSERT INTO member_block (blocker_id, blocked_id, created_at)"
                            + " VALUES (:blockerId, :blockedId, NOW(6))"
                            + " ON DUPLICATE KEY UPDATE member_block_id = member_block_id",
            nativeQuery = true)
    int insertIgnoringDuplicate(
            @Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "DELETE FROM MemberBlock mb WHERE mb.blocker.id = :blockerId AND mb.blocked.id = :blockedId")
    int deleteByPair(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Query(
            "SELECT COUNT(mb) > 0 FROM MemberBlock mb WHERE mb.blocker.id = :blockerId AND mb.blocked.id = :blockedId")
    boolean existsByPair(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    @Query(
            "SELECT mb FROM MemberBlock mb JOIN FETCH mb.blocked WHERE mb.blocker.id = :blockerId ORDER BY mb.id DESC")
    List<MemberBlock> findAllByBlocker(@Param("blockerId") Long blockerId, Pageable pageable);
}
