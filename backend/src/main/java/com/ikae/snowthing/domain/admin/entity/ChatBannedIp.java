package com.ikae.snowthing.domain.admin.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_banned_ip")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatBannedIp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "banned_ip", nullable = false, length = 45)
    private String bannedIp;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public ChatBannedIp(
            String bannedIp, String reason, LocalDateTime bannedUntil, String createdBy) {
        this.bannedIp = bannedIp;
        this.reason = reason;
        this.bannedUntil = bannedUntil;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return bannedUntil != null && bannedUntil.isBefore(LocalDateTime.now());
    }
}
