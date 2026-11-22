package com.ikae.snowthing.domain.resortreport.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.resortreport.entity.ResortReport;
import com.ikae.snowthing.domain.resortreport.entity.ResortReportStatus;

public interface ResortReportRepository extends JpaRepository<ResortReport, Long> {

    /** 자동 숨김(IKA-80)이 상태와 내용을 확인하는 동안 삭제나 수정이 끼어들지 않게 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResortReport r WHERE r.id = :reportId")
    Optional<ResortReport> findByIdForUpdate(@Param("reportId") Long reportId);

    @Query(
            value =
                    "SELECT r FROM ResortReport r "
                            + "JOIN FETCH r.resort "
                            + "JOIN FETCH r.author "
                            + "WHERE r.status = :status "
                            + "AND r.createdAt >= :start AND r.createdAt < :end "
                            + "ORDER BY r.createdAt DESC, r.id DESC",
            countQuery =
                    "SELECT COUNT(r) FROM ResortReport r "
                            + "WHERE r.status = :status "
                            + "AND r.createdAt >= :start AND r.createdAt < :end")
    Page<ResortReport> findTodayReports(
            @Param("status") ResortReportStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    @Query(
            value =
                    "SELECT r FROM ResortReport r "
                            + "JOIN FETCH r.resort "
                            + "JOIN FETCH r.author "
                            + "WHERE r.resort.id = :resortId AND r.status = :status "
                            + "AND r.createdAt >= :start AND r.createdAt < :end "
                            + "ORDER BY r.createdAt DESC, r.id DESC",
            countQuery =
                    "SELECT COUNT(r) FROM ResortReport r "
                            + "WHERE r.resort.id = :resortId AND r.status = :status "
                            + "AND r.createdAt >= :start AND r.createdAt < :end")
    Page<ResortReport> findTodayReportsByResortId(
            @Param("resortId") Long resortId,
            @Param("status") ResortReportStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    long countByAuthorIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long memberId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT r FROM ResortReport r JOIN FETCH r.author WHERE r.id = :reportId")
    java.util.Optional<ResortReport> findByIdWithAuthor(@Param("reportId") Long reportId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ResortReport r WHERE r.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
