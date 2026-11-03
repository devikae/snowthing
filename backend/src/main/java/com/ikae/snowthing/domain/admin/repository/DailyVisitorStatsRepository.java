package com.ikae.snowthing.domain.admin.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.admin.entity.DailyVisitorStats;

public interface DailyVisitorStatsRepository extends JpaRepository<DailyVisitorStats, LocalDate> {

    List<DailyVisitorStats> findByStatDateBetweenOrderByStatDateAsc(LocalDate start, LocalDate end);

    @Modifying
    @Query(
            value =
                    "INSERT INTO daily_visitor_stats (stat_date, total_visitors, member_visitors,"
                            + " guest_visitors, pv_count, updated_at) VALUES (:statDate, 1,"
                            + " IF(:isMember, 1, 0), IF(:isMember, 0, 1), 1, NOW(6)) ON DUPLICATE KEY"
                            + " UPDATE total_visitors = total_visitors + 1, member_visitors ="
                            + " member_visitors + IF(:isMember, 1, 0), guest_visitors = guest_visitors"
                            + " + IF(:isMember, 0, 1), pv_count = pv_count + 1, updated_at = NOW(6)",
            nativeQuery = true)
    void incrementVisitor(
            @Param("statDate") LocalDate statDate, @Param("isMember") boolean isMember);

    @Modifying
    @Query(
            value =
                    "INSERT INTO daily_visitor_stats (stat_date, total_visitors, member_visitors,"
                            + " guest_visitors, pv_count, updated_at) VALUES (:statDate, 0, 0, 0, 1,"
                            + " NOW(6)) ON DUPLICATE KEY UPDATE pv_count = pv_count + 1, updated_at ="
                            + " NOW(6)",
            nativeQuery = true)
    void incrementPv(@Param("statDate") LocalDate statDate);
}
