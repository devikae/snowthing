package com.ikae.snowthing.domain.member.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.dto.BlockedMemberResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberBlockRepository;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** 회원 차단. 차단은 쪽지 전용이 아니며, 다른 도메인은 {@link #isBlocked}로 관계를 확인한다. */
@Service
@RequiredArgsConstructor
public class MemberBlockService {

    /** 차단 목록은 설정 화면에서 한 번에 보여 준다. 비정상적으로 많은 경우를 대비해 최근 순으로 자른다. */
    static final int MAX_BLOCK_LIST_SIZE = 500;

    private final MemberBlockRepository memberBlockRepository;
    private final MemberRepository memberRepository;

    /** 이미 차단한 회원이면 그대로 성공한다. */
    @Transactional
    public void block(CustomUserDetails userDetails, String targetPublicId) {
        Long blockerId = userDetails.getMember().getId();
        Member target = findTarget(targetPublicId);
        if (target.getId().equals(blockerId)) {
            throw new CustomException(ErrorCode.MEMBER_BLOCK_SELF_NOT_ALLOWED);
        }
        memberBlockRepository.insertIgnoringDuplicate(blockerId, target.getId());
    }

    /** 차단하지 않은 회원이어도 그대로 성공한다. */
    @Transactional
    public void unblock(CustomUserDetails userDetails, String targetPublicId) {
        Member target = findTarget(targetPublicId);
        memberBlockRepository.deleteByPair(userDetails.getMember().getId(), target.getId());
    }

    @Transactional(readOnly = true)
    public List<BlockedMemberResponse> getMyBlocks(CustomUserDetails userDetails) {
        return memberBlockRepository
                .findAllByBlocker(
                        userDetails.getMember().getId(), PageRequest.of(0, MAX_BLOCK_LIST_SIZE))
                .stream()
                .map(BlockedMemberResponse::from)
                .toList();
    }

    /** blocker가 blocked를 차단했는지. */
    @Transactional(readOnly = true)
    public boolean isBlocked(Long blockerId, Long blockedId) {
        return memberBlockRepository.existsByPair(blockerId, blockedId);
    }

    private Member findTarget(String publicId) {
        return memberRepository
                .findByPublicId(publicId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
