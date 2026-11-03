package com.ikae.snowthing.domain.admin.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "banner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Banner(
            String title,
            String imageUrl,
            String linkUrl,
            int displayOrder,
            boolean isActive,
            LocalDateTime startAt,
            LocalDateTime endAt) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.displayOrder = displayOrder;
        this.isActive = isActive;
        this.startAt = startAt;
        this.endAt = endAt;
        this.createdAt = LocalDateTime.now();
    }

    public void update(
            String title,
            String imageUrl,
            String linkUrl,
            int displayOrder,
            boolean isActive,
            LocalDateTime startAt,
            LocalDateTime endAt) {
        this.title = title;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.displayOrder = displayOrder;
        this.isActive = isActive;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void toggleActive(boolean active) {
        this.isActive = active;
    }
}
