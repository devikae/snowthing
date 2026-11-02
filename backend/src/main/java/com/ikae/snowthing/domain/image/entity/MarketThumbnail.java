package com.ikae.snowthing.domain.image.entity;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(
        name = "market_thumbnail",
        indexes = @Index(name = "idx_market_thumbnail_due", columnList = "due_at"))
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

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    protected MarketThumbnail() {}
}
