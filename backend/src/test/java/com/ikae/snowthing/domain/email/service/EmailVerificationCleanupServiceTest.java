package com.ikae.snowthing.domain.email.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;

class EmailVerificationCleanupServiceTest {

    @Test
    void cleanupUsesCodeExpiryOlderThanTwentyFourHours() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-06T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        EmailVerificationRepository repository = mock(EmailVerificationRepository.class);
        EmailVerificationCleanupService service =
                new EmailVerificationCleanupService(repository, clock);

        service.deleteExpiredTerminalRecords();

        verify(repository).deleteExpiredBefore(LocalDateTime.now(clock).minusHours(24));
    }
}
