package com.ikae.snowthing.domain.member.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 탈퇴 후 재가입을 막는 이메일 해시. 원문 이메일은 저장하지 않는다. 쓰기는 {@code WithdrawnEmailRepository.upsert}로 한다. */
@Entity
@Table(
        name = "withdrawn_email",
        indexes = @Index(name = "idx_withdrawn_email_expires_at", columnList = "expires_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawnEmail {

    @Id
    @Column(name = "email_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String emailHash;

    @Column(name = "withdrawn_at", nullable = false)
    private LocalDateTime withdrawnAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
