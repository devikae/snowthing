package com.ikae.snowthing.domain.image.entity;

import java.time.Instant;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "profile_image_cleanup")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileImageCleanup {
    @Id
    @Column(name = "image_key", length = 100)
    private String key;

    @Column(name = "image_url", nullable = false, length = 500)
    private String url;

    @Column(name = "not_before", nullable = false)
    private Instant notBefore;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    public ProfileImageCleanup(String key, String url, Instant notBefore) {
        this.key = key;
        this.url = url;
        this.notBefore = notBefore;
    }

    public void retryAt(Instant next) {
        attempts++;
        notBefore = next;
    }
}
