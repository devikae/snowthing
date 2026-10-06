package com.ikae.snowthing.domain.email.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ikae.snowthing.domain.email.entity.EmailVerification;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EmailVerification e where e.email = :email and e.purpose = :purpose")
    Optional<EmailVerification> findForUpdate(
            @Param("email") String email, @Param("purpose") EmailVerificationPurpose purpose);

    Optional<EmailVerification> findByPublicIdAndPurpose(
            String publicId, EmailVerificationPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select e from EmailVerification e where e.publicId = :publicId and e.purpose = :purpose")
    Optional<EmailVerification> findByPublicIdAndPurposeForUpdate(
            @Param("publicId") String publicId, @Param("purpose") EmailVerificationPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select e from EmailVerification e where e.tokenDigest = :tokenDigest and e.purpose = :purpose")
    Optional<EmailVerification> findByTokenDigestAndPurposeForUpdate(
            @Param("tokenDigest") String tokenDigest,
            @Param("purpose") EmailVerificationPurpose purpose);

    @Modifying
    @Query(
            "delete from EmailVerification e where e.expiresAt < :cutoff"
                    + " and (e.tokenExpiresAt is null or e.tokenExpiresAt < :cutoff)")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
