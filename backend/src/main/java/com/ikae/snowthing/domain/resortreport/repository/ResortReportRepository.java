package com.ikae.snowthing.domain.resortreport.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.resortreport.entity.ResortReport;

public interface ResortReportRepository extends JpaRepository<ResortReport, Long> {

    @Query(
            "SELECT r FROM ResortReport r "
                    + "JOIN FETCH r.resort "
                    + "JOIN FETCH r.author "
                    + "WHERE r.createdAt >= :start "
                    + "ORDER BY r.createdAt DESC")
    List<ResortReport> findTodayReports(@Param("start") LocalDateTime start, Pageable pageable);

    @Query(
            "SELECT r FROM ResortReport r "
                    + "JOIN FETCH r.resort "
                    + "JOIN FETCH r.author "
                    + "WHERE r.resort.id = :resortId AND r.createdAt >= :start "
                    + "ORDER BY r.createdAt DESC")
    List<ResortReport> findTodayReportsByResortId(
            @Param("resortId") Long resortId,
            @Param("start") LocalDateTime start,
            Pageable pageable);
}
