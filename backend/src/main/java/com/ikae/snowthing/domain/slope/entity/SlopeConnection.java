package com.ikae.snowthing.domain.slope.entity;

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

import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 슬로프 사이 관계 (054). 자동 계산 초안을 검수해 확정한 값만 저장한다. 방향이 있어 양쪽 줄을 따로 둔다. */
@Entity
@Table(
        name = "slope_connection",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_slope_connection_pair",
                        columnNames = {"slope_id", "related_slope_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SlopeConnection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slope_connection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slope_id", nullable = false)
    private Slope slope;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "related_slope_id", nullable = false)
    private Slope relatedSlope;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 20)
    private SlopeRelation relation;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Builder
    public SlopeConnection(
            Slope slope, Slope relatedSlope, SlopeRelation relation, Integer distanceM) {
        this.slope = slope;
        this.relatedSlope = relatedSlope;
        this.relation = relation;
        this.distanceM = distanceM;
    }
}
