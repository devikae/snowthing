package com.ikae.snowthing.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.auth.oauth2.OAuthUserProfile;
import com.ikae.snowthing.domain.member.dto.RegistrationConsentRequest;
import com.ikae.snowthing.domain.member.dto.SocialAccountsResponse;
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

    /** 연결을 시작하기 전에 확인한다. 이미 같은 제공자가 연결돼 있으면 소셜 로그인 화면까지 보내지 않는다. */
    @Transactional(readOnly = true)
    public void requireProviderNotLinked(Long memberId, SocialProvider provider) {
        ensureProviderFree(memberId, provider);
    }

    /**
     * 로그인한 회원에게 소셜 계정을 붙인다. 다른 회원이 쓰는 소셜 계정은 합치지 않고 거부한다.
     *
     * <p>회원 행을 잠가서 같은 회원의 연결과 해제가 동시에 돌 때 로그인 방법 수를 잘못 세지 않게 한다.
     */
    @Transactional
    public void linkSocialAccount(Long memberId, OAuthUserProfile profile) {
        validateIdentity(profile);
        Member member = lockActiveMember(memberId);
        SocialProvider provider = profile.provider();
        String providerUserId = profile.providerUserId().trim();

        Optional<MemberSocialAccount> existing =
                memberSocialAccountRepository.findByProviderAndProviderUserId(
                        provider, providerUserId);
        if (existing.isPresent()) {
            boolean mine = memberId.equals(existing.get().getMember().getId());
            throw new CustomException(
                    mine
                            ? ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED
                            : ErrorCode.SOCIAL_ACCOUNT_IN_USE);
        }
        ensureProviderFree(memberId, provider);
        // provider_email 컬럼이 NOT NULL이라 가입과 같은 기준으로 이메일 동의를 요구한다.
        validateEmailPresent(profile);

        try {
            memberSocialAccountRepository.saveAndFlush(
                    MemberSocialAccount.builder()
                            .member(member)
                            .provider(provider)
                            .providerUserId(providerUserId)
                            .providerEmail(emailNormalizer.normalize(profile.email()))
                            .connectedAt(LocalDateTime.now(clock))
                            .build());
        } catch (DataIntegrityViolationException exception) {
            // 확인과 저장 사이에 다른 요청이 먼저 저장한 경우다. 회원 행 잠금은 다른 회원의 요청까지 막지 못한다.
            if (violatesConstraint(
                    exception, MemberSocialAccount.PROVIDER_USER_UNIQUE_CONSTRAINT)) {
                throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_IN_USE, exception);
            }
            if (violatesConstraint(
                    exception, MemberSocialAccount.MEMBER_PROVIDER_UNIQUE_CONSTRAINT)) {
                throw new CustomException(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED, exception);
            }
            throw exception;
        }
    }

    /** 연결을 해제한다. 해제한 뒤 로그인할 방법이 하나도 남지 않으면 거부한다. */
    @Transactional
    public void unlinkSocialAccount(Long memberId, SocialProvider provider) {
        Member member = lockActiveMember(memberId);
        List<MemberSocialAccount> accounts =
                memberSocialAccountRepository.findAllByMemberId(memberId);
        MemberSocialAccount target =
                accounts.stream()
                        .filter(account -> account.getProvider() == provider)
                        .findFirst()
                        .orElseThrow(
                                () -> new CustomException(ErrorCode.SOCIAL_ACCOUNT_NOT_LINKED));
        int remainingLoginMethods = accounts.size() - 1 + (hasPasswordLogin(member) ? 1 : 0);
        if (remainingLoginMethods < 1) {
            throw new CustomException(ErrorCode.SOCIAL_LAST_LOGIN_METHOD);
        }
        memberSocialAccountRepository.delete(target);
    }

    @Transactional(readOnly = true)
    public SocialAccountsResponse getSocialAccounts(Long memberId) {
        Member member =
                memberRepository
                        .findById(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        List<SocialAccountsResponse.LinkedAccount> accounts =
                memberSocialAccountRepository.findAllByMemberId(memberId).stream()
                        .sorted(
                                Comparator.comparing(MemberSocialAccount::getProvider)
                                        .thenComparing(MemberSocialAccount::getId))
                        .map(
                                account ->
                                        new SocialAccountsResponse.LinkedAccount(
                                                account.getProvider(), account.getConnectedAt()))
                        .toList();
        return new SocialAccountsResponse(hasPasswordLogin(member), accounts);
    }

    private void ensureProviderFree(Long memberId, SocialProvider provider) {
        if (memberSocialAccountRepository.existsByMemberIdAndProvider(memberId, provider)) {
            throw new CustomException(ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
        }
    }

    /** 이메일로 가입한 회원만 비밀번호가 있다. 소셜로 가입한 회원은 password가 null이다. */
    private boolean hasPasswordLogin(Member member) {
        return member.getPassword() != null;
    }

    private Member lockActiveMember(Long memberId) {
        Member member =
                memberRepository
                        .findByIdForUpdate(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        validateActiveStatus(member);
        return member;
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
