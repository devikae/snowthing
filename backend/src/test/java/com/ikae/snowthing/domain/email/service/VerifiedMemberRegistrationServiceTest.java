package com.ikae.snowthing.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.email.entity.EmailVerification;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.entity.EmailVerificationStatus;
import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;
import com.ikae.snowthing.domain.email.security.EmailVerificationHasher;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.domain.member.service.MemberService;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class VerifiedMemberRegistrationServiceTest {

    private static final String EMAIL = "member@snowthing.org";
    private static final String TOKEN = "sign-up-token";
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-06T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private EmailVerificationRepository verificationRepository;
    private MemberRepository memberRepository;
    private MemberService memberService;
    private EmailVerificationHasher hasher;
    private VerifiedMemberRegistrationService service;

    @BeforeEach
    void setUp() {
        verificationRepository = mock(EmailVerificationRepository.class);
        memberRepository = mock(MemberRepository.class);
        memberService = mock(MemberService.class);
        hasher = new EmailVerificationHasher("member-registration-test-secret-at-least-32-bytes");
        service =
                new VerifiedMemberRegistrationService(
                        verificationRepository,
                        hasher,
                        new EmailNormalizer(),
                        memberRepository,
                        memberService,
                        CLOCK);
    }

    @Test
    void verifiedTokenCreatesVerifiedMemberAndIsConsumedOnce() {
        EmailVerification verification = verifiedSignUp(TOKEN, 15);
        MemberSignUpRequest request = request(TOKEN);
        MemberSignUpResponse response =
                MemberSignUpResponse.builder()
                        .publicId("member-public-id")
                        .email(EMAIL)
                        .nickname("라이더")
                        .build();
        Member member =
                Member.builder()
                        .publicId("member-public-id")
                        .email(EMAIL)
                        .password("encoded")
                        .nickname("라이더")
                        .build();
        given(verificationRepository.findForUpdate(EMAIL, EmailVerificationPurpose.SIGN_UP))
                .willReturn(Optional.of(verification));
        given(memberService.signUp(request)).willReturn(response);
        given(memberRepository.findByPublicId("member-public-id")).willReturn(Optional.of(member));

        assertThat(service.register(request)).isSameAs(response);
        assertThat(member.getEmailVerifiedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(verification.getStatus()).isEqualTo(EmailVerificationStatus.CONSUMED);

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_TOKEN_EXPIRED);
    }

    @Test
    void tokenForAnotherEmailOrPurposeIsRejectedBeforeMemberCreation() {
        EmailVerification verification = verifiedSignUp("different-token", 15);
        given(verificationRepository.findForUpdate(EMAIL, EmailVerificationPurpose.SIGN_UP))
                .willReturn(Optional.of(verification));

        assertThatThrownBy(() -> service.register(request(TOKEN)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_TOKEN_INVALID);
    }

    private EmailVerification verifiedSignUp(String rawToken, long tokenMinutes) {
        LocalDateTime now = LocalDateTime.now(CLOCK);
        EmailVerification verification =
                new EmailVerification(
                        "request-id",
                        EMAIL,
                        EmailVerificationPurpose.SIGN_UP,
                        "code-digest",
                        "127.0.0.1",
                        now.minusMinutes(1),
                        now.plusMinutes(4));
        verification.markSent("message-id", now.minusMinutes(1));
        verification.verify(hasher.digestToken(rawToken), now, now.plusMinutes(tokenMinutes));
        return verification;
    }

    private MemberSignUpRequest request(String token) {
        return MemberSignUpRequest.builder()
                .email(" Member@SnowThing.org ")
                .password("Password1!")
                .emailVerificationToken(token)
                .nickname("라이더")
                .resortIds(List.of())
                .ridingStyleIds(List.of())
                .build();
    }
}
