package com.ikae.snowthing.domain.member.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member_marketing_consent_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class MemberMarketingConsentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_marketing_consent_history_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public MemberMarketingConsentHistory(Long memberId, boolean agreed, LocalDateTime changedAt) {
        this.memberId = memberId;
        this.agreed = agreed;
        this.changedAt = changedAt;
    }
}
