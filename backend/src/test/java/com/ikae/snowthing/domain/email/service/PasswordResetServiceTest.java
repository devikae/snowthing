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
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ikae.snowthing.domain.email.entity.EmailVerification;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.entity.EmailVerificationStatus;
import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;
import com.ikae.snowthing.domain.email.security.EmailVerificationHasher;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class PasswordResetServiceTest {

    private static final String EMAIL = "member@snowthing.org";
    private static final String TOKEN = "reset-token";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-06T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private EmailVerificationRepository verificationRepository;
    private MemberRepository memberRepository;
    private EmailVerificationHasher hasher;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        verificationRepository = mock(EmailVerificationRepository.class);
        memberRepository = mock(MemberRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        hasher = new EmailVerificationHasher("password-reset-test-secret-at-least-32-bytes");
        service =
                new PasswordResetService(
                        verificationRepository, hasher, memberRepository, passwordEncoder, CLOCK);
        given(passwordEncoder.encode("NewPassword1!")).willReturn("encoded-new-password");
    }

    @Test
    void validTokenChangesPasswordMarksEmailVerifiedAndConsumesToken() {
        EmailVerification verification = verifiedReset(TOKEN, 15);
        Member member =
                Member.builder()
                        .publicId("member-public-id")
                        .email(EMAIL)
                        .password("old-password")
                        .nickname("라이더")
                        .build();
        given(
                        verificationRepository.findByTokenDigestAndPurposeForUpdate(
                                hasher.digestToken(TOKEN), EmailVerificationPurpose.PASSWORD_RESET))
                .willReturn(Optional.of(verification));
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));

        String memberPublicId = service.reset(TOKEN, "NewPassword1!");

        assertThat(memberPublicId).isEqualTo("member-public-id");
        assertThat(member.getPassword()).isEqualTo("encoded-new-password");
        assertThat(member.getEmailVerifiedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.CONSUMED);
    }

    @Test
    void expiredOrConsumedTokenCannotChangePasswordAgain() {
        EmailVerification verification = verifiedReset(TOKEN, -1);
        given(
                        verificationRepository.findByTokenDigestAndPurposeForUpdate(
                                hasher.digestToken(TOKEN), EmailVerificationPurpose.PASSWORD_RESET))
                .willReturn(Optional.of(verification));

        assertThatThrownBy(() -> service.reset(TOKEN, "NewPassword1!"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_TOKEN_EXPIRED);
    }

    @Test
    void signUpPurposeTokenCannotBeUsedForPasswordReset() {
        given(
                        verificationRepository.findByTokenDigestAndPurposeForUpdate(
                                hasher.digestToken(TOKEN), EmailVerificationPurpose.PASSWORD_RESET))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.reset(TOKEN, "NewPassword1!"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_TOKEN_INVALID);
    }

    private EmailVerification verifiedReset(String rawToken, long tokenMinutes) {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        EmailVerification verification =
                new EmailVerification(
                        "request-id",
                        EMAIL,
                        EmailVerificationPurpose.PASSWORD_RESET,
                        "code-digest",
                        "127.0.0.1",
                        now.minusMinutes(1),
                        now.plusMinutes(4));
        verification.markSent("message-id", now.minusMinutes(1));
        verification.verify(hasher.digestToken(rawToken), now, now.plusMinutes(tokenMinutes));
        return verification;
    }
}
