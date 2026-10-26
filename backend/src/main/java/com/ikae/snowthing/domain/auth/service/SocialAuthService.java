package com.ikae.snowthing.domain.auth.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberSocialAccount;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberSocialAccountRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private static final int MAX_NICKNAME_GENERATION_ATTEMPTS = 5;

    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository memberSocialAccountRepository;
    private final EmailNormalizer emailNormalizer;
    private final SocialNicknameGenerator socialNicknameGenerator;
    private final Clock clock;

    @Transactional
    public Member authenticateOrRegister(OAuthUserProfile profile) {
        validateProfile(profile);

        String providerUserId = profile.providerUserId().trim();
        String normalizedEmail = emailNormalizer.normalize(profile.email());
        LocalDateTime now = LocalDateTime.now(clock);

        Optional<MemberSocialAccount> existingSocialAccount =
                memberSocialAccountRepository.findByProviderAndProviderUserId(
                        profile.provider(), providerUserId);
        if (existingSocialAccount.isPresent()) {
            Member linkedMember = existingSocialAccount.get().getMember();
            validateActiveStatus(linkedMember);
            if (linkedMember.getEmailVerifiedAt() == null) {
                linkedMember.verifyEmail(now);
            }
            return linkedMember;
        }

        Optional<Member> existingMemberByEmail = memberRepository.findByEmail(normalizedEmail);
        if (existingMemberByEmail.isPresent()) {
            Member member = existingMemberByEmail.get();
            validateActiveStatus(member);
            if (member.getEmailVerifiedAt() == null) {
                member.verifyEmail(now);
            }
            linkSocialAccountIfAbsent(member, profile, providerUserId, normalizedEmail, now);
            return member;
        }

        Member newMember = createSocialMember(normalizedEmail, profile.nickname(), now);
        saveSocialAccount(
                MemberSocialAccount.builder()
                        .member(newMember)
                        .provider(profile.provider())
                        .providerUserId(providerUserId)
                        .providerEmail(normalizedEmail)
                        .connectedAt(now)
                        .build());
        return newMember;
    }

    private void validateProfile(OAuthUserProfile profile) {
        if (profile == null
                || profile.provider() == null
                || profile.providerUserId() == null
                || profile.providerUserId().isBlank()) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
        if (profile.email() == null || profile.email().isBlank()) {
            throw new CustomException(ErrorCode.OAUTH_EMAIL_REQUIRED);
        }
        if (!profile.emailVerified()) {
            throw new CustomException(ErrorCode.OAUTH_EMAIL_NOT_VERIFIED);
        }
    }

    private void validateActiveStatus(Member member) {
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
    }

    private void linkSocialAccountIfAbsent(
            Member member,
            OAuthUserProfile profile,
            String providerUserId,
            String normalizedEmail,
            LocalDateTime now) {
        Optional<MemberSocialAccount> existingProviderLink =
                memberSocialAccountRepository.findByMemberIdAndProvider(
                        member.getId(), profile.provider());
        if (existingProviderLink.isPresent()) {
            if (!existingProviderLink.get().getProviderUserId().equals(providerUserId)) {
                throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
            }
            return;
        }
        saveSocialAccount(
                MemberSocialAccount.builder()
                        .member(member)
                        .provider(profile.provider())
                        .providerUserId(providerUserId)
                        .providerEmail(normalizedEmail)
                        .connectedAt(now)
                        .build());
    }

    private Member createSocialMember(
            String normalizedEmail, String rawNickname, LocalDateTime now) {
        for (int attempt = 0; attempt < MAX_NICKNAME_GENERATION_ATTEMPTS; attempt++) {
            String candidateNickname =
                    attempt == 0
                            ? socialNicknameGenerator.initialCandidate(rawNickname)
                            : socialNicknameGenerator.generateWithSuffix(rawNickname);
            if (memberRepository.existsByNickname(candidateNickname)) {
                continue;
            }
            Member member =
                    Member.builder()
                            .email(normalizedEmail)
                            .password(null)
                            .nickname(candidateNickname)
                            .emailVerifiedAt(now)
                            .role(Role.ROLE_USER)
                            .status(MemberStatus.ACTIVE)
                            .build();
            try {
                return memberRepository.saveAndFlush(member);
            } catch (DataIntegrityViolationException exception) {
                if (violatesConstraint(exception, Member.EMAIL_UNIQUE_CONSTRAINT)) {
                    throw new CustomException(ErrorCode.EMAIL_ALREADY_REGISTERED, exception);
                }
                if (violatesConstraint(exception, Member.NICKNAME_UNIQUE_CONSTRAINT)) {
                    throw new CustomException(ErrorCode.DUPLICATE_NICKNAME, exception);
                }
                throw exception;
            }
        }
        throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
    }

    private void saveSocialAccount(MemberSocialAccount socialAccount) {
        try {
            memberSocialAccountRepository.saveAndFlush(socialAccount);
        } catch (DataIntegrityViolationException exception) {
            if (violatesConstraint(exception, MemberSocialAccount.PROVIDER_USER_UNIQUE_CONSTRAINT)
                    || violatesConstraint(
                            exception, MemberSocialAccount.MEMBER_PROVIDER_UNIQUE_CONSTRAINT)) {
                throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED, exception);
            }
            throw exception;
        }
    }

    private boolean violatesConstraint(
            DataIntegrityViolationException exception, String expectedConstraintName) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && expectedConstraintName.equalsIgnoreCase(
                            constraintViolation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
