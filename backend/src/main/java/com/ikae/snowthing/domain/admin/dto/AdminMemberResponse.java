package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.entity.Role;

public record AdminMemberResponse(
        String publicId,
        String email,
        String nickname,
        String profileImageUrl,
        MemberStatus status,
        Role role,
        LocalDateTime createdAt) {

    public static AdminMemberResponse from(Member member) {
        return new AdminMemberResponse(
                member.getPublicId(),
                member.getEmail(),
                member.getNickname(),
                member.getProfileImageUrl(),
                member.getStatus(),
                member.getRole(),
                member.getCreatedAt());
    }
}
