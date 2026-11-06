package com.ikae.snowthing.domain.contentreport.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.contentreport.entity.ContentModerationAction;
import com.ikae.snowthing.domain.contentreport.entity.ReportTargetType;

public interface ContentModerationActionRepository
        extends JpaRepository<ContentModerationAction, Long> {

    @Query(
            "SELECT a FROM ContentModerationAction a "
                    + "WHERE a.actionType = com.ikae.snowthing.domain.contentreport.entity"
                    + ".ModerationActionType.TEMPORARY_HOLD "
                    + "AND a.releasedAt IS NULL AND a.expiresAt <= :now ORDER BY a.id")
    List<ContentModerationAction> findExpiredHolds(
            @Param("now") LocalDateTime now, Pageable pageable);

    @Query(
            "SELECT a FROM ContentModerationAction a "
                    + "WHERE a.actionType = com.ikae.snowthing.domain.contentreport.entity"
                    + ".ModerationActionType.TEMPORARY_HOLD "
                    + "AND a.releasedAt IS NULL "
                    + "AND a.targetType = :targetType AND a.targetId = :targetId")
    Optional<ContentModerationAction> findActiveHold(
            @Param("targetType") ReportTargetType targetType, @Param("targetId") Long targetId);
}
