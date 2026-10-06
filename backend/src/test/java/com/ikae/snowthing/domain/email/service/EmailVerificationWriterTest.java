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
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.email.entity.EmailVerification;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class EmailVerificationWriterTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-06T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private EmailVerificationRepository repository;
    private EmailVerificationWriter writer;

    @BeforeEach
    void setUp() {
        repository = mock(EmailVerificationRepository.class);
        writer = new EmailVerificationWriter(repository, CLOCK);
    }

    @Test
    void resendInsideSixtySecondsIsRejectedBeforeReplacingChallenge() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        EmailVerification verification = existingVerification(now.minusSeconds(30));
        given(repository.findForUpdate("member@snowthing.org", EmailVerificationPurpose.SIGN_UP))
                .willReturn(Optional.of(verification));

        assertThatThrownBy(
                        () ->
                                writer.prepare(
                                        "new-request",
                                        "member@snowthing.org",
                                        EmailVerificationPurpose.SIGN_UP,
                                        "new-digest",
                                        "127.0.0.1",
                                        now))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_LIMIT);
        assertThat(verification.getPublicId()).isEqualTo("old-request");
    }

    @Test
    void fifthSendInCurrentWindowIsTheLastAllowedSend() {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        EmailVerification verification = existingVerification(now.minusMinutes(10));
        for (int count = 1; count < 5; count++) {
            verification.replaceChallenge(
                    "request-" + count,
                    "digest-" + count,
                    "127.0.0.1",
                    now.minusMinutes(9 - count),
                    now.plusMinutes(5),
                    false);
        }
        verification.markSent("message-id", now.minusMinutes(2));
        given(repository.findForUpdate("member@snowthing.org", EmailVerificationPurpose.SIGN_UP))
                .willReturn(Optional.of(verification));

        assertThat(verification.getSendCount()).isEqualTo(5);
        assertThatThrownBy(
                        () ->
                                writer.prepare(
                                        "sixth-request",
                                        "member@snowthing.org",
                                        EmailVerificationPurpose.SIGN_UP,
                                        "sixth-digest",
                                        "127.0.0.1",
                                        now))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_LIMIT);
    }

    private EmailVerification existingVerification(LocalDateTime sentAt) {
        EmailVerification verification =
                new EmailVerification(
                        "old-request",
                        "member@snowthing.org",
                        EmailVerificationPurpose.SIGN_UP,
                        "old-digest",
                        "127.0.0.1",
                        sentAt,
                        sentAt.plusMinutes(5));
        ReflectionTestUtils.setField(verification, "id", 1L);
        verification.markSent("message-id", sentAt);
        return verification;
    }
}
