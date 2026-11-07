package com.ikae.snowthing.domain.member.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.aimember.AiMemberRoster;
import com.ikae.snowthing.domain.image.service.ProfileImageLifecycleService;
import com.ikae.snowthing.domain.member.dto.MemberWithdrawalRequest;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberSocialAccount;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberSocialAccountRepository;
import com.ikae.snowthing.domain.member.repository.MemberWithdrawalRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/**
 * 회원 탈퇴. 설계: docs/conception/025_member_withdrawal/01_design.md
 *
 * <p>회원 행을 잠근 뒤 처리하므로 동시에 두 번 요청해도 한 번만 탈퇴하고 나머지는 ACCOUNT_WITHDRAWN으로 끝난다. 세션 끊기는 커밋 뒤 컨트롤러에서 한다.
 */
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    public static final String SOCIAL_CONFIRMATION = "탈퇴";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final MemberSocialAccountRepository socialAccountRepository;
    private final MemberWithdrawalRepository withdrawalRepository;
    private final WithdrawnEmailService withdrawnEmails;
    private final ProfileImageLifecycleService profileImages;
    private final ObjectProvider<AiMemberRoster> aiRoster;
    private final PasswordEncoder passwordEncoder;
    private final EmailNormalizer emailNormalizer;
    private final Clock clock;

    /** 탈퇴하고 세션을 끊을 회원의 publicId를 돌려준다. */
    @Transactional
    public String withdraw(Long memberId, MemberWithdrawalRequest request) {
        Member member =
                memberRepository
                        .findByIdForUpdate(memberId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        requireWithdrawable(member);
        List<MemberSocialAccount> socialAccounts =
                socialAccountRepository.findAllByMemberId(memberId);
        verifyIdentity(member, socialAccounts, request);

        // 네이버 회원의 member.email은 내부 식별값이라 소셜 재가입 판정에 쓰고, 제공자 이메일은 같은 이메일 가입을 막는 데 쓴다.
        Set<String> emails = new LinkedHashSet<>();
        emails.add(emailNormalizer.normalize(member.getEmail()));
        socialAccounts.forEach(
                account -> emails.add(emailNormalizer.normalize(account.getProviderEmail())));

        // "_"와 ".invalid"는 가입 규칙으로 만들 수 없어 실제 회원의 닉네임, 이메일과 겹치지 않는다.
        String anonymizedEmail = "withdrawn-" + memberId + "@withdrawn.invalid";
        String anonymizedNickname = "withdrawn_" + memberId;
        withdrawalRepository.erasePersonalData(
                memberId,
                member.getNickname(),
                member.getEmail(),
                emails,
                anonymizedEmail,
                LocalDateTime.now(clock.withZone(KST)));
        profileImages.queueForCleanup(member.getProfileImageUrl());
        withdrawnEmails.block(emails);
        member.withdraw(anonymizedEmail, anonymizedNickname);
        return member.getPublicId();
    }

    private void requireWithdrawable(Member member) {
        // 정지 회원이 탈퇴 후 재가입으로 정지를 피하지 못하게 한다.
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
        AiMemberRoster roster = aiRoster.getIfAvailable();
        if (roster != null && roster.memberIds().contains(member.getId())) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void verifyIdentity(
            Member member,
            List<MemberSocialAccount> socialAccounts,
            MemberWithdrawalRequest request) {
        if (member.getPassword() != null) {
            if (request.password() == null
                    || !passwordEncoder.matches(request.password(), member.getPassword())) {
                throw new CustomException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
            }
            return;
        }
        // 비밀번호도 소셜 연결도 없는 계정은 로그인으로 만들어진 회원이 아니다(AI 멤버 기능을 끈 경우 포함).
        if (socialAccounts.isEmpty()) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }
        if (!SOCIAL_CONFIRMATION.equals(request.confirmation())) {
            throw new CustomException(ErrorCode.WITHDRAWAL_CONFIRMATION_MISMATCH);
        }
    }
}
