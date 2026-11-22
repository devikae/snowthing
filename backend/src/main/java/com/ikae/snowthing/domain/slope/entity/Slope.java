package com.ikae.snowthing.domain.slope.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리조트 공식 자료로 정리한 슬로프 제원 (054). 공식 값이 없으면 null이고 추정하지 않는다.
 *
 * <p>운영 여부는 운영 현황판(resort_slope_status)이 따로 다룬다. 이 엔티티는 시즌 중에 바뀌지 않는 제원만 둔다.
 */
@Entity
@Table(
        name = "slope",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_slope_resort_name",
                        columnNames = {"resort_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Slope extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slope_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resort_id", nullable = false)
    private Resort resort;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "name_local", length = 100)
    private String nameLocal;

    @Column(name = "official_difficulty", length = 30)
    private String officialDifficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 20)
    private SlopeDifficulty difficulty;

    @Column(name = "length_m")
    private Integer lengthM;

    @Column(name = "avg_gradient", precision = 4, scale = 1)
    private BigDecimal avgGradient;

    @Column(name = "max_gradient", precision = 4, scale = 1)
    private BigDecimal maxGradient;

    @Column(name = "width_m")
    private Integer widthM;

    @Column(name = "lift_name", length = 100)
    private String liftName;

    /** 코스 맵 선과 연결하는 OSM way ID 목록. 쉼표로 구분한다. */
    @Column(name = "osm_way_ids", length = 300)
    private String osmWayIds;

    @Column(name = "source_url", nullable = false, length = 500)
    private String sourceUrl;

    @Column(name = "checked_at", nullable = false)
    private LocalDate checkedAt;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Builder
    public Slope(
            Resort resort,
            String name,
            String nameLocal,
            String officialDifficulty,
            SlopeDifficulty difficulty,
            Integer lengthM,
            BigDecimal avgGradient,
            BigDecimal maxGradient,
            Integer widthM,
            String liftName,
            String osmWayIds,
            String sourceUrl,
            LocalDate checkedAt,
            int displayOrder) {
        this.resort = resort;
        this.name = name;
        this.nameLocal = nameLocal;
        this.officialDifficulty = officialDifficulty;
        this.difficulty = difficulty;
        this.lengthM = lengthM;
        this.avgGradient = avgGradient;
        this.maxGradient = maxGradient;
        this.widthM = widthM;
        this.liftName = liftName;
        this.osmWayIds = osmWayIds;
        this.sourceUrl = sourceUrl;
        this.checkedAt = checkedAt;
        this.displayOrder = displayOrder;
    }

    public List<Long> osmWayIdList() {
        if (osmWayIds == null || osmWayIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(osmWayIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
