package com.ikae.snowthing.global.idempotency;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * 작성 API의 Idempotency-Key 기록 (032).
 *
 * <p>글 저장과 같은 트랜잭션에서 먼저 insert한다. 같은 키의 두 번째 요청은 UNIQUE 충돌로 막히고, 첫 요청이 커밋되면 저장된 응답을 그대로 돌려받는다. 응답
 * 본문은 같은 트랜잭션 끝에서 채우므로 커밋된 기록에는 항상 응답이 있다.
 */
@Entity
@Table(
        name = "idempotency_record",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_idempotency_record_author_target_key",
                        columnNames = {"author_key", "target_type", "idempotency_key"}),
        indexes =
                @Index(
                        name = "idx_idempotency_record_created",
                        columnList = "created_at,idempotency_record_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "idempotency_record_id")
    private Long id;

    /** 회원은 member:{회원 ID}, 비로그인은 ip:{IP의 SHA-256 앞 32자}. IP 원문은 남기지 않는다. */
    @Column(
            name = "author_key",
            nullable = false,
            length = 80,
            columnDefinition = "varchar(80) character set utf8mb4 collate utf8mb4_bin")
    private String authorKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private IdempotencyTarget targetType;

    /** 소문자로 맞춘 UUID 36자. */
    @Column(
            name = "idempotency_key",
            nullable = false,
            length = 36,
            columnDefinition = "varchar(36) character set utf8mb4 collate utf8mb4_bin")
    private String idempotencyKey;

    /** 같은 키로 다른 내용을 보냈는지 판별하는 요청 본문 SHA-256. */
    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String requestHash;

    /** 처음 요청의 201 응답 JSON. insert 직후에는 비어 있고 같은 트랜잭션 끝에서 채운다. */
    @Column(name = "response_body", columnDefinition = "MEDIUMTEXT")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    IdempotencyRecord(
            String authorKey,
            IdempotencyTarget targetType,
            String idempotencyKey,
            String requestHash,
            LocalDateTime createdAt) {
        this.authorKey = authorKey;
        this.targetType = targetType;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
    }
}
