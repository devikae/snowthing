package com.ikae.snowthing.domain.member.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;

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
            BigDecimal routeLatitude,
            BigDecimal routeLongitude) {
        this.name = name;
        this.regionName = regionName;
        this.code = code;
        this.displayOrder = displayOrder;
        this.active = active;
        this.routeLatitude = routeLatitude;
        this.routeLongitude = routeLongitude;
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
