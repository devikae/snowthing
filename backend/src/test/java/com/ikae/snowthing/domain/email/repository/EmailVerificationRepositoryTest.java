package com.ikae.snowthing.domain.email.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.email.entity.EmailVerification;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;

@SpringBootTest
@Transactional
class EmailVerificationRepositoryTest {

    @Autowired private EmailVerificationRepository repository;

    @Autowired private EntityManager entityManager;

    @Test
    void cleanupRetainsRecordWithTokenValidAfterCutoff() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 5, 4, 20);
        LocalDateTime issuedAt = cutoff.minusDays(2);

        EmailVerification expired =
                verification(
                        "00000000-0000-0000-0000-000000000001",
                        "expired@snowthing.org",
                        issuedAt,
                        cutoff.minusHours(1));
        EmailVerification activeToken =
                verification(
                        "00000000-0000-0000-0000-000000000002",
                        "active-token@snowthing.org",
                        issuedAt,
                        cutoff.minusHours(1));
        activeToken.markSent("ses-message-id", issuedAt);
        activeToken.verify("b".repeat(64), issuedAt.plusMinutes(1), cutoff.plusHours(1));
        repository.saveAllAndFlush(java.util.List.of(expired, activeToken));

        int deleted = repository.deleteExpiredBefore(cutoff);
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(expired.getId())).isEmpty();
        assertThat(repository.findById(activeToken.getId())).isPresent();
    }

    private EmailVerification verification(
            String publicId, String email, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        return new EmailVerification(
                publicId,
                email,
                EmailVerificationPurpose.SIGN_UP,
                "a".repeat(64),
                "203.0.113.10",
                issuedAt,
                expiresAt);
    }
}
