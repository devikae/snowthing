package com.ikae.snowthing.domain.email.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "email_verification",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_email_verification_email_purpose",
                    columnNames = {"email", "purpose"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36)
    private String publicId;

    @Column(name = "email", nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private EmailVerificationPurpose purpose;

    @Column(name = "code_digest", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String codeDigest;

    @Column(name = "token_digest", length = 64, columnDefinition = "CHAR(64)")
    private String tokenDigest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailVerificationStatus status;

    @Column(name = "failed_attempt_count", nullable = false)
    private int failedAttemptCount;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "send_window_started_at", nullable = false)
    private LocalDateTime sendWindowStartedAt;

    @Column(name = "request_ip", nullable = false, length = 45)
    private String requestIp;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "ses_message_id", length = 255)
    private String sesMessageId;

    public EmailVerification(
            String publicId,
            String email,
            EmailVerificationPurpose purpose,
            String codeDigest,
            String requestIp,
            LocalDateTime now,
            LocalDateTime expiresAt) {
        this.email = email;
        this.purpose = purpose;
        replaceChallenge(publicId, codeDigest, requestIp, now, expiresAt, true);
    }

    public void replaceChallenge(
            String publicId,
            String codeDigest,
            String requestIp,
            LocalDateTime now,
            LocalDateTime expiresAt,
            boolean resetWindow) {
        this.publicId = publicId;
        this.codeDigest = codeDigest;
        this.tokenDigest = null;
        this.status = EmailVerificationStatus.PENDING;
        this.failedAttemptCount = 0;
        this.requestIp = requestIp;
        this.sentAt = null;
        this.expiresAt = expiresAt;
        this.verifiedAt = null;
        this.tokenExpiresAt = null;
        this.consumedAt = null;
        this.sesMessageId = null;
        if (resetWindow) {
            this.sendWindowStartedAt = now;
            this.sendCount = 1;
        } else {
            this.sendCount++;
        }
    }

    public void markSent(String messageId, LocalDateTime sentAt) {
        this.sesMessageId = messageId;
        this.sentAt = sentAt;
        this.status = EmailVerificationStatus.SENT;
    }

    public void markSendFailed() {
        this.status = EmailVerificationStatus.SEND_FAILED;
    }

    public void recordFailedAttempt(int maxAttempts) {
        this.failedAttemptCount++;
        if (this.failedAttemptCount >= maxAttempts) {
            this.status = EmailVerificationStatus.FAILED;
        }
    }

    public void markExpired() {
        this.status = EmailVerificationStatus.EXPIRED;
    }

    public void verify(String tokenDigest, LocalDateTime now, LocalDateTime tokenExpiresAt) {
        this.tokenDigest = tokenDigest;
        this.verifiedAt = now;
        this.tokenExpiresAt = tokenExpiresAt;
        this.status = EmailVerificationStatus.VERIFIED;
    }

    public void consume(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
        this.status = EmailVerificationStatus.CONSUMED;
    }
}
