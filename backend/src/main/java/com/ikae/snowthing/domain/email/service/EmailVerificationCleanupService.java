package com.ikae.snowthing.domain.email.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailVerificationCleanupService {

    private static final Duration TERMINAL_RETENTION = Duration.ofHours(24);

    private final EmailVerificationRepository repository;
    private final Clock clock;

    @Transactional
    @Scheduled(cron = "${snowthing.email.verification.cleanup-cron:0 20 4 * * *}")
    public void deleteExpiredTerminalRecords() {
        repository.deleteExpiredBefore(LocalDateTime.now(clock).minus(TERMINAL_RETENTION));
    }
}
