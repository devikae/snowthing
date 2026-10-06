package com.ikae.snowthing.domain.email.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.email.EmailVerificationPolicy;
import com.ikae.snowthing.domain.email.entity.EmailVerification;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailVerificationWriter {

    private final EmailVerificationRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailVerification prepare(
            String requestId,
            String email,
            EmailVerificationPurpose purpose,
            String digest,
            String requestIp,
            LocalDateTime now) {
        EmailVerification verification =
                repository
                        .findForUpdate(email, purpose)
                        .orElseGet(
                                () ->
                                        new EmailVerification(
                                                requestId,
                                                email,
                                                purpose,
                                                digest,
                                                requestIp,
                                                now,
                                                now.plus(EmailVerificationPolicy.CODE_TTL)));
        if (verification.getId() != null) {
            LocalDateTime lastRequestAt =
                    verification.getSentAt() != null
                            ? verification.getSentAt()
                            : verification.getUpdatedAt();
            if (lastRequestAt != null
                    && lastRequestAt.plus(EmailVerificationPolicy.RESEND_DELAY).isAfter(now)) {
                throw new CustomException(ErrorCode.EMAIL_SEND_LIMIT);
            }
            boolean resetWindow =
                    verification
                            .getSendWindowStartedAt()
                            .plus(EmailVerificationPolicy.SEND_WINDOW)
                            .isBefore(now);
            if (!resetWindow
                    && verification.getSendCount() >= EmailVerificationPolicy.MAX_SENDS_PER_EMAIL) {
                throw new CustomException(ErrorCode.EMAIL_SEND_LIMIT);
            }
            verification.replaceChallenge(
                    requestId,
                    digest,
                    requestIp,
                    now,
                    now.plus(EmailVerificationPolicy.CODE_TTL),
                    resetWindow);
        }
        return repository.saveAndFlush(verification);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(String requestId, EmailVerificationPurpose purpose, String messageId) {
        repository
                .findByPublicIdAndPurposeForUpdate(requestId, purpose)
                .ifPresent(value -> value.markSent(messageId, LocalDateTime.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String requestId, EmailVerificationPurpose purpose) {
        repository
                .findByPublicIdAndPurposeForUpdate(requestId, purpose)
                .ifPresent(EmailVerification::markSendFailed);
    }
}
