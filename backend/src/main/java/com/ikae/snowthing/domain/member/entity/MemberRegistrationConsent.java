package com.ikae.snowthing.domain.member.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_registration_consent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class MemberRegistrationConsent {
    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "privacy_version", nullable = false, length = 50)
    private String privacyVersion;

    @Column(name = "age_confirmed", nullable = false)
    private boolean ageConfirmed;

    @Column(name = "marketing_email_agreed", nullable = false)
    private boolean marketingEmailAgreed;

    @Column(name = "confirmed_at", nullable = false)
    private LocalDateTime confirmedAt;

    public MemberRegistrationConsent(
            Long memberId, boolean marketingEmailAgreed, LocalDateTime confirmedAt) {
        this.memberId = memberId;
        this.privacyVersion = "draft-2026-10-30";
        this.ageConfirmed = true;
        this.marketingEmailAgreed = marketingEmailAgreed;
        this.confirmedAt = confirmedAt;
    }
}
