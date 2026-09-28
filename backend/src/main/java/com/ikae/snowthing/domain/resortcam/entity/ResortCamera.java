package com.ikae.snowthing.domain.resortcam.entity;

import jakarta.persistence.*;

import com.ikae.snowthing.domain.member.entity.Resort;
import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "resort_camera",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_resort_camera_resort_code",
                        columnNames = {"resort_id", "code"}),
        indexes =
                @Index(
                        name = "idx_resort_camera_active_order",
                        columnList = "resort_id,is_active,display_order,resort_camera_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResortCamera extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "resort_camera_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resort_id", nullable = false)
    private Resort resort;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private CameraSourceType sourceType;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "external_page_url", length = 1000)
    private String externalPageUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    public ResortCamera(
            Resort resort,
            String code,
            String name,
            CameraSourceType sourceType,
            String sourceUrl,
            String externalPageUrl,
            int displayOrder,
            boolean active) {
        this.resort = resort;
        this.code = code;
        this.name = name;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.externalPageUrl = externalPageUrl;
        this.displayOrder = displayOrder;
        this.active = active;
    }
}
