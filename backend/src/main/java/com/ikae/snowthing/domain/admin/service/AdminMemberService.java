package com.ikae.snowthing.domain.admin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.admin.dto.AdminMemberResponse;
import com.ikae.snowthing.domain.image.service.ProfileImageLifecycleService;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final MemberRepository memberRepository;
    private final ProfileImageLifecycleService profileImageLifecycleService;

    @Transactional(readOnly = true)
    public Page<AdminMemberResponse> getMembers(
            String query, MemberStatus status, Pageable pageable) {
        Page<Member> members;
        if (query != null && !query.isBlank()) {
            members =
                    memberRepository.findByEmailContainingOrNicknameContaining(
                            query.trim(), query.trim(), pageable);
        } else if (status != null) {
            members = memberRepository.findByStatus(status, pageable);
        } else {
            members = memberRepository.findAll(pageable);
        }
        return members.map(AdminMemberResponse::from);
    }

    @Transactional
    public void updateStatus(String publicId, MemberStatus status) {
        Member member =
                memberRepository
                        .findByPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        member.updateStatus(status);
        log.info("Member status updated by admin: memberId={}, status={}", publicId, status);
    }

    @Transactional
    public void updateNickname(String publicId, String newNickname) {
        Member member =
                memberRepository
                        .findByPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        if (!member.getNickname().equals(newNickname)
                && memberRepository.existsByNickname(newNickname)) {
            throw new CustomException(ErrorCode.DUPLICATE_NICKNAME);
        }

        member.updateProfile(
                newNickname,
                member.getBio(),
                member.getDepartureRegion(),
                member.getProfileImageUrl());
        log.info(
                "Member nickname changed by admin: memberId={}, newNickname={}",
                publicId,
                newNickname);
    }

    public void resetProfileImage(String publicId) {
        Member member =
                memberRepository
                        .findByPublicId(publicId)
                        .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        profileImageLifecycleService.reset(member.getEmail());
        log.info("Member profile image forcefully cleared by admin: memberId={}", publicId);
    }
}
