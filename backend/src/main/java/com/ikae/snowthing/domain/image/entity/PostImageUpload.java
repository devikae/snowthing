package com.ikae.snowthing.domain.image.entity;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(
        name = "post_image_upload",
        indexes = @Index(name = "idx_image_upload_due", columnList = "due_at"))
public class PostImageUpload {
    @Id
    @Column(name = "image_key", length = 160)
    private String key;

    @Column(name = "owner_email", nullable = false, length = 255)
    private String owner;

    @Column(name = "state", nullable = false, length = 20)
    private String state;

    @Column(name = "due_at", nullable = false)
    private Instant due;

    protected PostImageUpload() {}
}
