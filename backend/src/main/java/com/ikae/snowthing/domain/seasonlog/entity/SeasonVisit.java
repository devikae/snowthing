package com.ikae.snowthing.domain.seasonlog.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Resort;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원이 리조트를 다녀온 날 하나. (회원, 날짜) 유니크라 하루에 한 번만 남는다. 운영 DDL: 044_migration_season_log.sql */
@Entity
@Table(
        name = "season_visit",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = SeasonVisit.MEMBER_DATE_UNIQUE,
                    columnNames = {"member_id", "visit_date"})
        },
        indexes = {@Index(name = "idx_season_visit_resort", columnList = "resort_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonVisit {

    public static final String MEMBER_DATE_UNIQUE = "uk_season_visit_member_date";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "season_visit_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resort_id", nullable = false)
    private Resort resort;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SeasonVisit(Member member, Resort resort, LocalDate visitDate, LocalDateTime createdAt) {
        this.member = member;
        this.resort = resort;
        this.visitDate = visitDate;
        this.createdAt = createdAt;
    }
}
