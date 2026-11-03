package com.ikae.snowthing.domain.admin.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "daily_visitor_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyVisitorLog {

    @EmbeddedId private DailyVisitorLogId id;

    @Column(name = "is_member", nullable = false)
    private boolean isMember;

    @Column(name = "first_visited_at", nullable = false)
    private LocalDateTime firstVisitedAt;

    public DailyVisitorLog(DailyVisitorLogId id, boolean isMember) {
        this.id = id;
        this.isMember = isMember;
        this.firstVisitedAt = LocalDateTime.now();
    }
}
