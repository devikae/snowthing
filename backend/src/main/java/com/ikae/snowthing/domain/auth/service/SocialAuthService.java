package com.ikae.snowthing.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.member.dto.RegistrationConsentRequest;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberSocialAccount;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.member.entity.SocialProvider;
import com.ikae.snowthing.domain.member.event.MemberSignedUpEvent;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberSocialAccountRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.domain.member.service.RegistrationConsentService;
import com.ikae.snowthing.domain.member.service.WithdrawnEmailService;
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
    private final ApplicationEventPublisher eventPublisher;
    private final RegistrationConsentService consentService;
    private final WithdrawnEmailService withdrawnEmails;

    /**
     * 콜백에서 호출한다. 연결된 회원이 있으면 돌려주고, 처음 온 사용자면 비어 있는 값을 돌려준다. 이때 회원을 만들지 않는다. 가입할 수 없는 사용자는 동의 화면까지
     * 보내지 않도록 여기서 거부한다.
     */
    @Transactional(readOnly = true)
    public Optional<Member> findMemberForLogin(OAuthUserProfile profile) {
        validateIdentity(profile);
        Optional<Member> linkedMember = findLinkedMember(profile);
        if (linkedMember.isEmpty()) {
            resolveNewMemberEmail(profile);
        }
        return linkedMember;
    }

    /** 동의를 마친 뒤 회원, 소셜 계정, 동의 기록을 한 트랜잭션으로 저장한다. */
    @Transactional
    public Member completeSignUp(OAuthUserProfile profile, RegistrationConsentRequest consent) {
        validateIdentity(profile);
        consentService.validate(consent);

        Optional<Member> linkedMember = findLinkedMember(profile);
        if (linkedMember.isPresent()) {
            return linkedMember.get();
        }

        String memberEmail = resolveNewMemberEmail(profile);
        String providerUserId = profile.providerUserId().trim();
        String providerEmail = emailNormalizer.normalize(profile.email());
        LocalDateTime now = LocalDateTime.now(clock);

        Member newMember =
                createSocialMember(
                        memberEmail, profile.nickname(), profile.emailAuthoritative(), now);
        consentService.record(newMember.getId(), consent, now);
        saveSocialAccount(
                MemberSocialAccount.builder()
                        .member(newMember)
                        .provider(profile.provider())
                        .providerUserId(providerUserId)
                        .providerEmail(providerEmail)
                        .connectedAt(now)
                        .build());
        eventPublisher.publishEvent(
                new MemberSignedUpEvent(newMember.getNickname(), profile.provider().name()));
        return newMember;
    }

    private Optional<Member> findLinkedMember(OAuthUserProfile profile) {
        Optional<Member> linkedMember =
                memberSocialAccountRepository
                        .findByProviderAndProviderUserId(
                                profile.provider(), profile.providerUserId().trim())
                        .map(MemberSocialAccount::getMember);
        linkedMember.ifPresent(this::validateActiveStatus);
        return linkedMember;
    }

    private String resolveNewMemberEmail(OAuthUserProfile profile) {
        validateEmailPresent(profile);
        String providerEmail = emailNormalizer.normalize(profile.email());
        withdrawnEmails.requireNotBlocked(providerEmail);
        if (!profile.emailAuthoritative()) {
            String internalEmail =
                    createInternalEmail(profile.provider(), profile.providerUserId().trim());
            withdrawnEmails.requireNotBlocked(internalEmail);
            return internalEmail;
        }
        String memberEmail = providerEmail;
        if (memberRepository.findByEmail(memberEmail).isPresent()) {
            throw new CustomException(ErrorCode.OAUTH_ACCOUNT_LINK_REQUIRED);
        }
        return memberEmail;
    }

    private void validateIdentity(OAuthUserProfile profile) {
        if (profile == null
                || profile.provider() == null
                || profile.providerUserId() == null
                || profile.providerUserId().isBlank()) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED);
        }
    }

    private void validateEmailPresent(OAuthUserProfile profile) {
        if (profile.email() == null || profile.email().isBlank()) {
            throw new CustomException(ErrorCode.OAUTH_EMAIL_REQUIRED);
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

    private Member createSocialMember(
            String memberEmail, String rawNickname, boolean emailAuthoritative, LocalDateTime now) {
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
                            .email(memberEmail)
                            .password(null)
                            .nickname(candidateNickname)
                            .emailVerifiedAt(emailAuthoritative ? now : null)
                            .role(Role.ROLE_USER)
                            .status(MemberStatus.ACTIVE)
                            .build();
            try {
                return memberRepository.saveAndFlush(member);
            } catch (DataIntegrityViolationException exception) {
                if (violatesConstraint(exception, Member.EMAIL_UNIQUE_CONSTRAINT)) {
                    ErrorCode errorCode =
                            emailAuthoritative
                                    ? ErrorCode.OAUTH_ACCOUNT_LINK_REQUIRED
                                    : ErrorCode.OAUTH_AUTHENTICATION_FAILED;
                    throw new CustomException(errorCode, exception);
                }
                if (violatesConstraint(exception, Member.NICKNAME_UNIQUE_CONSTRAINT)) {
                    throw new CustomException(ErrorCode.DUPLICATE_NICKNAME, exception);
                }
                throw exception;
            }
        }
        throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
    }

    private String createInternalEmail(SocialProvider provider, String providerUserId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] identifier =
                    (provider.name() + ":" + providerUserId).getBytes(StandardCharsets.UTF_8);
            String encoded =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(digest.digest(identifier));
            return "oauth-"
                    + provider.name().toLowerCase(Locale.ROOT)
                    + "-"
                    + encoded
                    + "@users.invalid";
        } catch (NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.OAUTH_AUTHENTICATION_FAILED, exception);
        }
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
