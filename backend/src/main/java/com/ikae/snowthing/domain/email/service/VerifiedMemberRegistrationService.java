package com.ikae.snowthing.domain.email.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VerifiedMemberRegistrationService {

    private final EmailVerificationRepository verificationRepository;
    private final EmailVerificationHasher hasher;
    private final EmailNormalizer normalizer;
    private final MemberRepository memberRepository;
    private final MemberService memberService;
    private final Clock clock;

    @Transactional
    public MemberSignUpResponse register(MemberSignUpRequest request) {
        String email = normalizer.normalize(request.getEmail());
        EmailVerification verification =
                verificationRepository
                        .findForUpdate(email, EmailVerificationPurpose.SIGN_UP)
                        .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_TOKEN_INVALID));
        validateToken(verification, request.getEmailVerificationToken());
        MemberSignUpResponse response = memberService.signUp(request);
        Member member =
                memberRepository
                        .findByPublicId(response.getPublicId())
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        member.verifyEmail(now);
        verification.consume(now);
        return response;
    }

    private void validateToken(EmailVerification verification, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new CustomException(ErrorCode.EMAIL_TOKEN_INVALID);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (verification.getStatus() != EmailVerificationStatus.VERIFIED
                || verification.getTokenExpiresAt() == null
                || !verification.getTokenExpiresAt().isAfter(now)) {
            throw new CustomException(ErrorCode.EMAIL_TOKEN_EXPIRED);
        }
        if (!hasher.matches(verification.getTokenDigest(), hasher.digestToken(rawToken))) {
            throw new CustomException(ErrorCode.EMAIL_TOKEN_INVALID);
        }
    }
}
