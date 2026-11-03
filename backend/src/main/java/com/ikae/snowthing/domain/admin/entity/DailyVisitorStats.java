package com.ikae.snowthing.domain.admin.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_visitor_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyVisitorStats {

    @Id
    @Column(name = "stat_date")
    private LocalDate statDate;

    @Column(name = "total_visitors", nullable = false)
    private int totalVisitors;

    @Column(name = "member_visitors", nullable = false)
    private int memberVisitors;

    @Column(name = "guest_visitors", nullable = false)
    private int guestVisitors;

    @Column(name = "pv_count", nullable = false)
    private int pvCount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public DailyVisitorStats(
            LocalDate statDate,
            int totalVisitors,
            int memberVisitors,
            int guestVisitors,
            int pvCount) {
        this.statDate = statDate;
        this.totalVisitors = totalVisitors;
        this.memberVisitors = memberVisitors;
        this.guestVisitors = guestVisitors;
        this.pvCount = pvCount;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementVisitor(boolean isMember) {
        this.totalVisitors++;
        if (isMember) {
            this.memberVisitors++;
        } else {
            this.guestVisitors++;
        }
        this.pvCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementPv() {
        this.pvCount++;
        this.updatedAt = LocalDateTime.now();
    }
}
