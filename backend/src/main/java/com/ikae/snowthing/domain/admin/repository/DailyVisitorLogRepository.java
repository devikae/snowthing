package com.ikae.snowthing.domain.admin.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.admin.entity.DailyVisitorLog;
import com.ikae.snowthing.domain.admin.entity.DailyVisitorLogId;

public interface DailyVisitorLogRepository
        extends JpaRepository<DailyVisitorLog, DailyVisitorLogId> {

    @Modifying
    @Query(
            value =
                    "INSERT IGNORE INTO daily_visitor_log (visit_date, visitor_hash, is_member,"
                        + " first_visited_at) VALUES (:visitDate, :visitorHash, :isMember,"
                        + " :firstVisitedAt)",
            nativeQuery = true)
    int insertIgnore(
            @Param("visitDate") LocalDate visitDate,
            @Param("visitorHash") String visitorHash,
            @Param("isMember") boolean isMember,
            @Param("firstVisitedAt") LocalDateTime firstVisitedAt);
}
