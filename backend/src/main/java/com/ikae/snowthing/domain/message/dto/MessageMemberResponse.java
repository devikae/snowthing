package com.ikae.snowthing.domain.message.dto;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;

/** 쪽지 상대. 탈퇴한 회원은 publicId와 사진을 비우고 "탈퇴한 회원"으로 보여 준다. */
public record MessageMemberResponse(
        String publicId, String nickname, String profileImageUrl, boolean withdrawn) {

    public static final String WITHDRAWN_NICKNAME = "탈퇴한 회원";

    public static MessageMemberResponse from(Member member) {
        if (member.getStatus() == MemberStatus.WITHDRAWN) {
            return new MessageMemberResponse(null, WITHDRAWN_NICKNAME, null, true);
        }
        return new MessageMemberResponse(
                member.getPublicId(), member.getNickname(), member.getProfileImageUrl(), false);
    }
}
