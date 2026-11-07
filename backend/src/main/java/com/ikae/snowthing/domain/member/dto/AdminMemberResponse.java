package com.ikae.snowthing.domain.member.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;

/** 관리자 화면에 필요한 값만 내보낸다. 비밀번호와 내부 ID는 넣지 않는다. */
public record AdminMemberResponse(
        String publicId,
        String email,
        String nickname,
        MemberStatus status,
        Role role,
        LocalDateTime createdAt) {

    public static AdminMemberResponse from(Member member) {
        return new AdminMemberResponse(
                member.getPublicId(),
                member.getEmail(),
                member.getNickname(),
                member.getStatus(),
                member.getRole(),
                member.getCreatedAt());
    }
}
