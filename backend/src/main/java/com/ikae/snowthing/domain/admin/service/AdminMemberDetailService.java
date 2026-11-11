package com.ikae.snowthing.domain.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminActionLogItem;
import com.ikae.snowthing.domain.admin.dto.AdminMemberDetailResponse;
import com.ikae.snowthing.domain.admin.log.AdminTargetType;
import com.ikae.snowthing.domain.admin.repository.AdminQueryRepository;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 관리자 회원 상세 (IKA-78). 로그인 방법은 비밀번호 유무와 연결된 소셜 제공자 이름만 준다. */
@Service
@RequiredArgsConstructor
public class AdminMemberDetailService {

    private static final int RECENT_LIMIT = 10;
    private static final int HISTORY_LIMIT = 20;

    private final MemberRepository memberRepository;
    private final AdminQueryRepository adminQueryRepository;

    @Transactional(readOnly = true)
    public AdminMemberDetailResponse get(CustomUserDetails admin, String publicId) {
        AdminAccess.require(admin);
        Member member =
                memberRepository
                        .findByPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        Long memberId = member.getId();
        List<AdminActionLogItem> history =
                adminQueryRepository.findActionLogs(
                        null, AdminTargetType.MEMBER.name(), publicId, null, 0, HISTORY_LIMIT);
        return new AdminMemberDetailResponse(
                member.getPublicId(),
                AdminMemberDetailResponse.maskEmail(member.getEmail()),
                member.getNickname(),
                member.getStatus().name(),
                member.getRole().name(),
                member.getCreatedAt(),
                member.getEmailVerifiedAt() != null,
                member.getPassword() != null && !member.getPassword().isBlank(),
                adminQueryRepository.findSocialProviders(memberId),
                adminQueryRepository.countPostsByMember(memberId),
                adminQueryRepository.countCommentsByMember(memberId),
                adminQueryRepository.findRecentPosts(memberId, RECENT_LIMIT),
                adminQueryRepository.findRecentComments(memberId, RECENT_LIMIT),
                history);
    }
}
