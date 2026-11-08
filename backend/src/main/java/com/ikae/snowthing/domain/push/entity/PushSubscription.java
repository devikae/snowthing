package com.ikae.snowthing.domain.push.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브라우저(기기) 하나의 웹 푸시 구독. 저장과 갱신은 {@code PushSubscriptionRepository.upsert} 한 문장으로 하고, 엔티티는 발송할 때 읽기만
 * 한다.
 *
 * <p>회원은 연관 대신 ID로 둔다. 발송은 트랜잭션 밖 비동기 스레드에서 돌아 지연 로딩을 쓸 수 없다.
 */
@Entity
@Table(
        name = "push_subscription",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_push_subscription_endpoint_hash",
                        columnNames = "endpoint_hash"),
        indexes =
                @Index(
                        name = "idx_push_subscription_member",
                        columnList = "member_id,last_used_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_subscription_id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 1024)
    private String endpoint;

    @Column(name = "endpoint_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String endpointHash;

    @Column(nullable = false, length = 128)
    private String p256dh;

    @Column(nullable = false, length = 64)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;
}
