package com.ikae.snowthing.domain.image.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "post_image_upload",
        indexes = @Index(name = "idx_image_upload_due", columnList = "due_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
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

    public void renewDue(Instant newDue) {
        this.due = newDue;
    }

    public void markDeleting(Instant deleteDue) {
        this.state = "DELETING";
        this.due = deleteDue;
    }

    public void markDeleted() {
        this.state = "DELETED";
    }
}
