package com.ikae.snowthing.domain.member.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;

import org.hibernate.annotations.ColumnDefault;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "resort")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resort {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resort_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "region", nullable = false, length = 50)
    private String regionName;

    @Column(name = "code", nullable = false, length = 30, unique = true)
    private String code;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    /** 054 마이그레이션과 같은 기본값. ddl-auto update로 컬럼이 생길 때 기존 행이 빈 값이 되지 않게 한다. */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'KR'")
    @Column(name = "country", nullable = false, length = 2)
    private ResortCountry country;

    @Column(name = "route_latitude", precision = 10, scale = 7)
    private BigDecimal routeLatitude;

    @Column(name = "route_longitude", precision = 10, scale = 7)
    private BigDecimal routeLongitude;

    @Builder
    public Resort(
            String name,
            String regionName,
            String code,
            int displayOrder,
            boolean active,
            ResortCountry country,
            BigDecimal routeLatitude,
            BigDecimal routeLongitude) {
        this.name = name;
        this.regionName = regionName;
        this.code = code;
        this.displayOrder = displayOrder;
        this.active = active;
        this.country = country == null ? ResortCountry.KR : country;
        this.routeLatitude = routeLatitude;
        this.routeLongitude = routeLongitude;
    }

    /** 커뮤니티 기능에서 고를 수 있는 리조트인지. 해외 리조트는 슬로프 제원 비교에만 쓴다. */
    public boolean isServiceTarget() {
        return active && country == ResortCountry.KR;
    }

    public void updateMetadata(String code, int displayOrder, boolean active) {
        this.code = code;
        this.displayOrder = displayOrder;
        this.active = active;
    }

    public void updateRouteCoordinate(BigDecimal routeLatitude, BigDecimal routeLongitude) {
        this.routeLatitude = routeLatitude;
        this.routeLongitude = routeLongitude;
    }
}
