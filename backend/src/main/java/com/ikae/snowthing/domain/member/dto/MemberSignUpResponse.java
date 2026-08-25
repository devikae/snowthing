package com.ikae.snowthing.domain.member.dto;

import com.ikae.snowthing.domain.member.entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MemberSignUpResponse {

    private final String publicId;
    private final String email;
    private final String nickname;
    private final LocalDateTime createdAt;

    @Builder
    public MemberSignUpResponse(String publicId, String email, String nickname, LocalDateTime createdAt) {
        this.publicId = publicId;
        this.email = email;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public static MemberSignUpResponse from(Member member) {
        return MemberSignUpResponse.builder()
                .publicId(member.getPublicId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
