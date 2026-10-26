package com.ikae.snowthing.domain.member.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.ikae.snowthing.global.common.BaseTimeEntity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "member_social_account",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_member_social_provider_user",
                    columnNames = {"provider", "provider_user_id"}),
            @UniqueConstraint(
                    name = "uk_member_social_member_provider",
                    columnNames = {"member_id", "provider"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    @Column(name = "provider_email", nullable = false, length = 100)
    private String providerEmail;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;

    @Builder
    public MemberSocialAccount(
            Member member,
            SocialProvider provider,
            String providerUserId,
            String providerEmail,
            LocalDateTime connectedAt) {
        this.member = member;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.providerEmail = providerEmail;
        this.connectedAt = connectedAt != null ? connectedAt : LocalDateTime.now();
    }
}
