package com.ikae.snowthing.domain.image.entity;

import java.time.Instant;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "market_thumbnail",
        indexes = @Index(name = "idx_market_thumbnail_due", columnList = "due_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MarketThumbnail {
    @Id
    @Column(name = "post_public_id", length = 36)
    private String postPublicId;

    @Column(name = "source_key", length = 500)
    private String sourceKey;

    @Column(name = "ready_source_key", length = 500)
    private String readySourceKey;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;
}
