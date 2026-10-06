package com.ikae.snowthing.domain.email.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.email.entity.EmailVerification;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.entity.EmailVerificationStatus;
import com.ikae.snowthing.domain.email.repository.EmailVerificationRepository;
import com.ikae.snowthing.domain.email.security.EmailVerificationHasher;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final EmailVerificationRepository verificationRepository;
    private final EmailVerificationHasher hasher;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional
    public String reset(String rawToken, String newPassword) {
        String tokenDigest = hasher.digestToken(rawToken);
        EmailVerification verification =
                verificationRepository
                        .findByTokenDigestAndPurposeForUpdate(
                                tokenDigest, EmailVerificationPurpose.PASSWORD_RESET)
                        .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_TOKEN_INVALID));
        LocalDateTime now = LocalDateTime.now(clock);
        if (verification.getStatus() != EmailVerificationStatus.VERIFIED
                || verification.getTokenExpiresAt() == null
                || !verification.getTokenExpiresAt().isAfter(now)) {
            throw new CustomException(ErrorCode.EMAIL_TOKEN_EXPIRED);
        }
        Member member =
                memberRepository
                        .findByEmail(verification.getEmail())
                        .orElseThrow(() -> new CustomException(ErrorCode.EMAIL_TOKEN_INVALID));
        member.changePassword(passwordEncoder.encode(newPassword));
        member.verifyEmail(now);
        verification.consume(now);
        return member.getPublicId();
    }
}
