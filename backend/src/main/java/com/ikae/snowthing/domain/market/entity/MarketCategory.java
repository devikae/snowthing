package com.ikae.snowthing.domain.market.entity;

import jakarta.persistence.*;

import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "market_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MarketCategory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "market_category_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    public MarketCategory(String code, String name, int sortOrder, boolean active) {
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
        this.active = active;
    }
}
