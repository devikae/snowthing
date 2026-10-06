package com.ikae.snowthing.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.email.dto.EmailVerificationSendResponse;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.mail.VerificationEmailSender;
import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;
import com.ikae.snowthing.domain.email.security.EmailVerificationHasher;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class EmailVerificationRequestServiceTest {

    private MemberRepository memberRepository;
    private EmailVerificationWriter writer;
    private EmailVerificationIpRateLimiter rateLimiter;
    private VerificationEmailSender emailSender;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        memberRepository = mock(MemberRepository.class);
        writer = mock(EmailVerificationWriter.class);
        rateLimiter = mock(EmailVerificationIpRateLimiter.class);
        emailSender = mock(VerificationEmailSender.class);
        service =
                new EmailVerificationService(
                        memberRepository,
                        mock(EmailVerificationRepository.class),
                        writer,
                        new EmailVerificationHasher("email-request-test-secret-at-least-32-bytes"),
                        new EmailNormalizer(),
                        rateLimiter,
                        emailSender,
                        Clock.fixed(
                                Instant.parse("2026-10-06T03:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    @Test
    void signUpRequestNormalizesEmailAndPersistsBeforeSending() {
        given(memberRepository.existsByEmail("member@snowthing.org")).willReturn(false);
        given(
                        emailSender.sendVerificationCode(
                                eq("member@snowthing.org"),
                                matches("^[0-9]{6}$"),
                                eq(EmailVerificationPurpose.SIGN_UP)))
                .willReturn("ses-message-id");

        EmailVerificationSendResponse response =
                service.requestSignUp(" Member@SnowThing.org ", "203.0.113.10");

        assertThat(response.requestId()).isNotBlank();
        assertThat(response.expiresInSeconds()).isEqualTo(300);
        assertThat(response.resendAvailableInSeconds()).isEqualTo(60);
        verify(rateLimiter).check("203.0.113.10");
        verify(writer)
                .prepare(
                        eq(response.requestId()),
                        eq("member@snowthing.org"),
                        eq(EmailVerificationPurpose.SIGN_UP),
                        anyString(),
                        eq("203.0.113.10"),
                        any());
        verify(writer)
                .markSent(response.requestId(), EmailVerificationPurpose.SIGN_UP, "ses-message-id");
    }

    @Test
    void registeredEmailUsesEmail001AndDoesNotCallRateLimiterOrSes() {
        given(memberRepository.existsByEmail("member@snowthing.org")).willReturn(true);

        assertThatThrownBy(() -> service.requestSignUp("member@snowthing.org", "203.0.113.10"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_ALREADY_REGISTERED);
        verifyNoInteractions(rateLimiter, writer, emailSender);
    }

    @Test
    void unknownPasswordResetEmailReturnsOpaqueRequestWithoutSendingMail() {
        given(memberRepository.existsByEmail("unknown@snowthing.org")).willReturn(false);

        EmailVerificationSendResponse response =
                service.requestPasswordReset("unknown@snowthing.org", "203.0.113.10");

        assertThat(response.requestId()).isNotBlank();
        verify(rateLimiter).check("203.0.113.10");
        verifyNoInteractions(writer, emailSender);
    }

    @Test
    void existingPasswordResetEmailHidesDeliveryFailureBehindNormalResponse() {
        given(memberRepository.existsByEmail("member@snowthing.org")).willReturn(true);
        given(
                        emailSender.sendVerificationCode(
                                eq("member@snowthing.org"),
                                anyString(),
                                eq(EmailVerificationPurpose.PASSWORD_RESET)))
                .willThrow(new CustomException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE));

        EmailVerificationSendResponse response =
                service.requestPasswordReset("member@snowthing.org", "203.0.113.10");

        assertThat(response.requestId()).isNotBlank();
        verify(writer).markFailed(anyString(), eq(EmailVerificationPurpose.PASSWORD_RESET));
    }
}
