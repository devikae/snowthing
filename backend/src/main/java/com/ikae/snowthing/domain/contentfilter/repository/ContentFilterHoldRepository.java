package com.ikae.snowthing.domain.contentfilter.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.contentfilter.entity.ContentFilterHold;
import com.ikae.snowthing.domain.contentfilter.entity.FilterTargetType;
import com.ikae.snowthing.domain.post.entity.Post;

public interface ContentFilterHoldRepository extends JpaRepository<ContentFilterHold, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM ContentFilterHold h WHERE h.id = :id")
    Optional<ContentFilterHold> findByIdForUpdate(@Param("id") Long id);

    Optional<ContentFilterHold> findByTargetTypeAndTargetId(
            FilterTargetType targetType, Long targetId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(
            "DELETE FROM ContentFilterHold h WHERE h.targetType = :targetType AND h.targetId ="
                    + " :targetId")
    int deleteByTarget(
            @Param("targetType") FilterTargetType targetType, @Param("targetId") Long targetId);

    /**
     * 보는 사람 본인이 쓴 보류 글. 회원은 회원 ID로, 비회원은 같은 IP이고 보류 뒤 짧은 시간 안일 때만 찾는다. 게시판 첫 쪽에 작성자 본인 글을 끼워 넣을 때
     * 쓴다.
     */
    @Query(
            "SELECT p FROM Post p JOIN FETCH p.category LEFT JOIN FETCH p.member"
                    + " WHERE p.status = com.ikae.snowthing.domain.post.entity.PostStatus.FILTER_HOLD"
                    + " AND p.id IN (SELECT h.targetId FROM ContentFilterHold h"
                    + " WHERE h.targetType = com.ikae.snowthing.domain.contentfilter.entity.FilterTargetType.POST"
                    + " AND (h.memberId = :memberId"
                    + " OR (h.memberId IS NULL AND h.writerIp = :writerIp AND h.heldAt >= :since)))")
    List<Post> findHeldPostsOfViewer(
            @Param("memberId") Long memberId,
            @Param("writerIp") String writerIp,
            @Param("since") LocalDateTime since);
}
