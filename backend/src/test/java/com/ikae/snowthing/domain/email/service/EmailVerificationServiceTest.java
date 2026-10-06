package com.ikae.snowthing.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.email.dto.EmailVerificationTokenResponse;
import com.ikae.snowthing.domain.email.entity.EmailVerification;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.entity.EmailVerificationStatus;
import com.ikae.snowthing.domain.email.mail.VerificationEmailSender;
import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;
import com.ikae.snowthing.domain.email.security.EmailVerificationHasher;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class EmailVerificationServiceTest {

    private static final String SECRET = "email-verification-unit-test-secret-32-bytes";
    private static final String REQUEST_ID = "request-id";
    private static final String EMAIL = "member@snowthing.org";
    private static final String CODE = "012345";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-06T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private EmailVerificationRepository repository;
    private EmailVerificationHasher hasher;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmailVerificationRepository.class);
        hasher = new EmailVerificationHasher(SECRET);
        service =
                new EmailVerificationService(
                        mock(MemberRepository.class),
                        repository,
                        mock(EmailVerificationWriter.class),
                        hasher,
                        new EmailNormalizer(),
                        mock(EmailVerificationIpRateLimiter.class),
                        mock(VerificationEmailSender.class),
                        CLOCK);
    }

    @Test
    void correctCodeIssuesOneTimeTokenWithoutStoringRawCode() {
        EmailVerification verification = sentVerification(CODE);
        given(
                        repository.findByPublicIdAndPurposeForUpdate(
                                REQUEST_ID, EmailVerificationPurpose.SIGN_UP))
                .willReturn(Optional.of(verification));

        EmailVerificationTokenResponse response =
                service.confirm(REQUEST_ID, CODE, EmailVerificationPurpose.SIGN_UP);

        assertThat(response.token()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(15 * 60);
        assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.VERIFIED);
        assertThat(verification.getCodeDigest()).doesNotContain(CODE);
        assertThat(verification.getTokenDigest()).doesNotContain(response.token());
    }

    @Test
    void fifthWrongCodeLocksChallenge() {
        EmailVerification verification = sentVerification(CODE);
        given(
                        repository.findByPublicIdAndPurposeForUpdate(
                                REQUEST_ID, EmailVerificationPurpose.SIGN_UP))
                .willReturn(Optional.of(verification));

        for (int attempt = 1; attempt < 5; attempt++) {
            assertThatThrownBy(
                            () ->
                                    service.confirm(
                                            REQUEST_ID, "999999", EmailVerificationPurpose.SIGN_UP))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMAIL_CODE_INVALID);
        }

        assertThatThrownBy(
                        () ->
                                service.confirm(
                                        REQUEST_ID, "999999", EmailVerificationPurpose.SIGN_UP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ATTEMPT_LIMIT);
        assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.FAILED);
    }

    @Test
    void malformedCodeUsesTheDocumentedFormatError() {
        assertThatThrownBy(
                        () ->
                                service.confirm(
                                        REQUEST_ID, "12345", EmailVerificationPurpose.SIGN_UP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_CODE_FORMAT_INVALID);
    }

    @Test
    void requestForDifferentPurposeCannotConfirmChallenge() {
        given(
                        repository.findByPublicIdAndPurposeForUpdate(
                                REQUEST_ID, EmailVerificationPurpose.PASSWORD_RESET))
                .willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.confirm(
                                        REQUEST_ID, CODE, EmailVerificationPurpose.PASSWORD_RESET))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_CODE_INVALID);
    }

    @Test
    void expiredCodeIsRejectedAndMarkedExpired() {
        LocalDateTime issuedAt = LocalDateTime.now(CLOCK).minusMinutes(10);
        String digest =
                hasher.digestCode(REQUEST_ID, EMAIL, EmailVerificationPurpose.SIGN_UP, CODE);
        EmailVerification verification =
                new EmailVerification(
                        REQUEST_ID,
                        EMAIL,
                        EmailVerificationPurpose.SIGN_UP,
                        digest,
                        "127.0.0.1",
                        issuedAt,
                        issuedAt.plusMinutes(5));
        verification.markSent("message-id", issuedAt);
        given(
                        repository.findByPublicIdAndPurposeForUpdate(
                                REQUEST_ID, EmailVerificationPurpose.SIGN_UP))
                .willReturn(Optional.of(verification));

        assertThatThrownBy(
                        () -> service.confirm(REQUEST_ID, CODE, EmailVerificationPurpose.SIGN_UP))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_CODE_EXPIRED);
        assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.EXPIRED);
    }

    private EmailVerification sentVerification(String rawCode) {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        String digest =
                hasher.digestCode(REQUEST_ID, EMAIL, EmailVerificationPurpose.SIGN_UP, rawCode);
        EmailVerification verification =
                new EmailVerification(
                        REQUEST_ID,
                        EMAIL,
                        EmailVerificationPurpose.SIGN_UP,
                        digest,
                        "127.0.0.1",
                        now,
                        now.plusMinutes(5));
        verification.markSent("message-id", now);
        return verification;
    }
}
