package com.ikae.snowthing.domain.contentreport.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ikae.snowthing.domain.contentreport.entity.ContentReport;
import com.ikae.snowthing.domain.contentreport.entity.ReportStatus;
import com.ikae.snowthing.domain.contentreport.entity.ReportTargetType;

public interface ContentReportRepository extends JpaRepository<ContentReport, Long> {

    @EntityGraph(attributePaths = "reporter")
    Page<ContentReport> findByStatus(ReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "reporter")
    List<ContentReport> findByTargetTypeAndTargetId(ReportTargetType targetType, Long targetId);

    Optional<ContentReport> findByTargetTypeAndTargetIdAndReporter_Id(
            ReportTargetType targetType, Long targetId, Long reporterId);

    @EntityGraph(attributePaths = "reporter")
    List<ContentReport> findByTargetTypeAndTargetIdAndStatus(
            ReportTargetType targetType, Long targetId, ReportStatus status);
}
