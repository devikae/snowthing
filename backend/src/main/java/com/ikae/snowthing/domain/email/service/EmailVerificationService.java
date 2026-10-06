package com.ikae.snowthing.domain.email.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.email.EmailVerificationPolicy;
import com.ikae.snowthing.domain.email.dto.EmailVerificationSendResponse;
import com.ikae.snowthing.domain.email.dto.EmailVerificationTokenResponse;
import com.ikae.snowthing.domain.email.entity.EmailVerification;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.entity.EmailVerificationStatus;
import com.ikae.snowthing.domain.email.mail.EmailDeliveryUncertainException;
import com.ikae.snowthing.domain.email.mail.VerificationEmailSender;
import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;
import com.ikae.snowthing.domain.email.security.EmailVerificationHasher;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final int CODE_BOUND = 1_000_000;
    private static final String CODE_PATTERN = "^[0-9]{6}$";
    private static final int TOKEN_BYTES = 32;
    private static final long PASSWORD_RESET_MIN_RESPONSE_MILLIS = 400L;
    private static final int PASSWORD_RESET_JITTER_BOUND_MILLIS = 100;

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository verificationRepository;
    private final EmailVerificationWriter writer;
    private final EmailVerificationHasher hasher;
    private final EmailNormalizer normalizer;
    private final EmailVerificationIpRateLimiter ipRateLimiter;
    private final VerificationEmailSender emailSender;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public boolean isEmailAvailable(String email) {
        return !memberRepository.existsByEmail(normalizer.normalize(email));
    }

    public EmailVerificationSendResponse requestSignUp(String email, String clientIp) {
        String normalizedEmail = normalizer.normalize(email);
        if (memberRepository.existsByEmail(normalizedEmail)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        return send(normalizedEmail, EmailVerificationPurpose.SIGN_UP, clientIp);
    }

    public EmailVerificationSendResponse requestPasswordReset(String email, String clientIp) {
        long startedAt = System.nanoTime();
        String normalizedEmail = normalizer.normalize(email);
        ipRateLimiter.check(clientIp);
        try {
            if (!memberRepository.existsByEmail(normalizedEmail)) {
                return response(UUID.randomUUID().toString());
            }
            try {
                return sendAfterIpCheck(
                        normalizedEmail, EmailVerificationPurpose.PASSWORD_RESET, clientIp);
            } catch (CustomException exception) {
                if (exception.getErrorCode() == ErrorCode.EMAIL_DELIVERY_UNAVAILABLE
                        || exception.getErrorCode() == ErrorCode.EMAIL_SEND_LIMIT) {
                    log.warn(
                            "Password reset email was not sent: {}",
                            exception.getErrorCode().getCode());
                    return response(UUID.randomUUID().toString());
                }
                throw exception;
            }
        } finally {
            padPasswordResetResponse(startedAt);
        }
    }

    @Transactional(noRollbackFor = CustomException.class)
    public EmailVerificationTokenResponse confirm(
            String requestId, String code, EmailVerificationPurpose purpose) {
        if (code == null || !code.matches(CODE_PATTERN)) {
            throw new CustomException(ErrorCode.EMAIL_CODE_FORMAT_INVALID);
        }
        EmailVerification verification =
                verificationRepository
                        .findByPublicIdAndPurposeForUpdate(requestId, purpose)
                        .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_CODE_INVALID));
        LocalDateTime now = LocalDateTime.now(clock);
        if (verification.getStatus() == EmailVerificationStatus.FAILED) {
            throw new CustomException(ErrorCode.EMAIL_ATTEMPT_LIMIT);
        }
        if (verification.getStatus() != EmailVerificationStatus.SENT) {
            throw new CustomException(ErrorCode.EMAIL_CODE_INVALID);
        }
        if (!verification.getExpiresAt().isAfter(now)) {
            verification.markExpired();
            throw new CustomException(ErrorCode.EMAIL_CODE_EXPIRED);
        }
        String actual =
                hasher.digestCode(
                        requestId, verification.getEmail(), verification.getPurpose(), code);
        if (!hasher.matches(verification.getCodeDigest(), actual)) {
            verification.recordFailedAttempt(EmailVerificationPolicy.MAX_FAILED_ATTEMPTS);
            if (verification.getStatus() == EmailVerificationStatus.FAILED) {
                throw new CustomException(ErrorCode.EMAIL_ATTEMPT_LIMIT);
            }
            throw new CustomException(ErrorCode.EMAIL_CODE_INVALID);
        }
        String token = randomToken();
        verification.verify(
                hasher.digestToken(token), now, now.plus(EmailVerificationPolicy.TOKEN_TTL));
        return new EmailVerificationTokenResponse(
                token, EmailVerificationPolicy.TOKEN_TTL.toSeconds());
    }

    private EmailVerificationSendResponse send(
            String email, EmailVerificationPurpose purpose, String clientIp) {
        ipRateLimiter.check(clientIp);
        return sendAfterIpCheck(email, purpose, clientIp);
    }

    private EmailVerificationSendResponse sendAfterIpCheck(
            String email, EmailVerificationPurpose purpose, String clientIp) {
        String requestId = UUID.randomUUID().toString();
        String code = String.format("%06d", secureRandom.nextInt(CODE_BOUND));
        String digest = hasher.digestCode(requestId, email, purpose, code);
        try {
            writer.prepare(requestId, email, purpose, digest, clientIp, LocalDateTime.now(clock));
        } catch (DataIntegrityViolationException exception) {
            writer.prepare(requestId, email, purpose, digest, clientIp, LocalDateTime.now(clock));
        }
        try {
            String messageId = emailSender.sendVerificationCode(email, code, purpose);
            writer.markSent(requestId, purpose, messageId);
            return response(requestId);
        } catch (EmailDeliveryUncertainException exception) {
            log.warn(
                    "Verification email delivery result is uncertain: purpose={}, requestId={}",
                    purpose,
                    requestId);
            writer.markSent(requestId, purpose, null);
            return response(requestId);
        } catch (RuntimeException exception) {
            writer.markFailed(requestId, purpose);
            throw exception;
        }
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void padPasswordResetResponse(long startedAt) {
        long targetMillis =
                PASSWORD_RESET_MIN_RESPONSE_MILLIS
                        + secureRandom.nextInt(PASSWORD_RESET_JITTER_BOUND_MILLIS);
        long elapsedNanos = System.nanoTime() - startedAt;
        long remainingNanos = TimeUnit.MILLISECONDS.toNanos(targetMillis) - elapsedNanos;
        if (remainingNanos > 0) {
            LockSupport.parkNanos(remainingNanos);
        }
    }

    private EmailVerificationSendResponse response(String requestId) {
        return new EmailVerificationSendResponse(
                requestId,
                EmailVerificationPolicy.CODE_TTL.toSeconds(),
                EmailVerificationPolicy.RESEND_DELAY.toSeconds());
    }
}
