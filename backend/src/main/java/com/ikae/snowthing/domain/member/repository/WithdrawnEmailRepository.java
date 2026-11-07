package com.ikae.snowthing.domain.member.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.entity.WithdrawnEmail;

public interface WithdrawnEmailRepository extends JpaRepository<WithdrawnEmail, String> {

    boolean existsByEmailHashAndExpiresAtAfter(String emailHash, LocalDateTime now);

    /** 같은 이메일로 두 번째 탈퇴가 와도 키 충돌 없이 기간만 늘린다. */
    @Modifying
    @Query(
            value =
                    "INSERT INTO withdrawn_email (email_hash, withdrawn_at, expires_at)"
                            + " VALUES (:emailHash, :withdrawnAt, :expiresAt)"
                            + " ON DUPLICATE KEY UPDATE withdrawn_at = VALUES(withdrawn_at),"
                            + " expires_at = VALUES(expires_at)",
            nativeQuery = true)
    int upsert(
            @Param("emailHash") String emailHash,
            @Param("withdrawnAt") LocalDateTime withdrawnAt,
            @Param("expiresAt") LocalDateTime expiresAt);

    @Transactional
    @Modifying
    @Query(
            value =
                    "DELETE FROM withdrawn_email WHERE expires_at <= :now"
                            + " ORDER BY expires_at, email_hash LIMIT :limit",
            nativeQuery = true)
    int deleteExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
