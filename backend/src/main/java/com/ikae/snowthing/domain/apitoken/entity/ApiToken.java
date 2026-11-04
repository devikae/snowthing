package com.ikae.snowthing.domain.apitoken.entity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.ikae.snowthing.domain.member.entity.Member;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원에 묶인 제한 API 토큰 (029). 평문은 만들 때 한 번만 응답하고 저장하지 않는다. 저장하는 값은 SHA-256 해시와 화면 표시용 접두사뿐이다.
 *
 * <p>토큰은 32바이트 난수라 추측 공격에 느린 해시가 필요 없다. 해시가 같으면 같은 토큰이므로 해시 컬럼에 유일 인덱스를 두고 바로 찾는다.
 */
@Entity
@Table(
        name = "api_token",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_api_token_hash", columnNames = "token_hash"),
            @UniqueConstraint(name = "uk_api_token_public_id", columnNames = "public_id")
        },
        indexes = {
            @Index(name = "idx_api_token_member_created", columnList = "member_id,created_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "api_token_id")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 36)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "member_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_api_token_member"))
    private Member member;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "token_prefix", nullable = false, length = 16)
    private String tokenPrefix;

    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    /** 쉼표로 구분한 범위 이름. 예: {@code DRAFT,READ}. */
    @Column(name = "scopes", nullable = false, length = 50)
    private String scopes;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ApiToken issue(
            Member member,
            String name,
            String tokenPrefix,
            String tokenHash,
            Collection<ApiTokenScope> scopes,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        ApiToken token = new ApiToken();
        token.publicId = UUID.randomUUID().toString();
        token.member = member;
        token.name = name;
        token.tokenPrefix = tokenPrefix;
        token.tokenHash = tokenHash;
        token.scopes =
                EnumSet.copyOf(scopes).stream()
                        .map(Enum::name)
                        .sorted()
                        .collect(Collectors.joining(","));
        token.createdAt = createdAt;
        token.expiresAt = expiresAt;
        return token;
    }

    public Set<ApiTokenScope> scopeSet() {
        return Arrays.stream(scopes.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(ApiTokenScope::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApiTokenScope.class)));
    }

    public boolean isUsableAt(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(LocalDateTime now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
