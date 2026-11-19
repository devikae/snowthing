package com.ikae.snowthing.domain.admin.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 리조트 시즌 개장일 (IKA-87). 리조트 한 곳에 한 줄이고, 관리자는 날짜와 표시 설정만 바꾼다. 리조트 추가는 마이그레이션으로 한다. */
@Entity
@Table(
        name = "season_opening",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_season_opening_resort_code",
                        columnNames = "resort_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeasonOpening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "season_opening_id")
    private Long id;

    @Column(name = "resort_code", nullable = false, length = 30)
    private String resortCode;

    @Column(name = "resort_name", nullable = false, length = 50)
    private String resortName;

    @Column(name = "opening_date")
    private LocalDate openingDate;

    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    @Column(name = "show_on_banner", nullable = false)
    private boolean showOnBanner;

    @Column(name = "banner_order", nullable = false)
    private int bannerOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public SeasonOpening(
            String resortCode,
            String resortName,
            LocalDate openingDate,
            boolean confirmed,
            boolean showOnBanner,
            int bannerOrder) {
        this.resortCode = resortCode;
        this.resortName = resortName;
        this.openingDate = openingDate;
        this.confirmed = confirmed;
        this.showOnBanner = showOnBanner;
        this.bannerOrder = bannerOrder;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** 값이 실제로 바뀌었으면 true. 바뀐 리조트만 조치 기록에 남기려고 쓴다. */
    public boolean update(
            LocalDate openingDate, boolean confirmed, boolean showOnBanner, int bannerOrder) {
        boolean changed =
                !Objects.equals(this.openingDate, openingDate)
                        || this.confirmed != confirmed
                        || this.showOnBanner != showOnBanner
                        || this.bannerOrder != bannerOrder;
        if (!changed) {
            return false;
        }
        this.openingDate = openingDate;
        this.confirmed = confirmed;
        this.showOnBanner = showOnBanner;
        this.bannerOrder = bannerOrder;
        this.updatedAt = LocalDateTime.now();
        return true;
    }
}
