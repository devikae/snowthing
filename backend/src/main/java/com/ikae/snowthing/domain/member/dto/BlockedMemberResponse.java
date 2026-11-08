package com.ikae.snowthing.domain.member.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberBlock;
import com.ikae.snowthing.domain.member.entity.MemberStatus;

/** 내 차단 목록 한 줄. 탈퇴한 회원도 차단을 풀 수 있도록 publicId는 그대로 두고 닉네임과 사진만 가린다. */
public record BlockedMemberResponse(
        String publicId,
        String nickname,
        String profileImageUrl,
        boolean withdrawn,
        LocalDateTime blockedAt) {

    public static BlockedMemberResponse from(MemberBlock block) {
        Member blocked = block.getBlocked();
        boolean withdrawn = blocked.getStatus() == MemberStatus.WITHDRAWN;
        return new BlockedMemberResponse(
                blocked.getPublicId(),
                withdrawn ? "탈퇴한 회원" : blocked.getNickname(),
                withdrawn ? null : blocked.getProfileImageUrl(),
                withdrawn,
                block.getCreatedAt());
    }
}
