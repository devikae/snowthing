package com.ikae.snowthing.domain.auth.dto;

import java.util.List;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;

import lombok.Builder;
import lombok.Getter;

@Getter
public class MemberLoginResponse {

    private final String publicId;
    private final String email;
    private final String nickname;
    private final Role role;
    private final List<String> resortNames;
    private final List<String> ridingStyleNames;

    @Builder
    public MemberLoginResponse(
            String publicId,
            String email,
            String nickname,
            Role role,
            List<String> resortNames,
            List<String> ridingStyleNames) {
        this.publicId = publicId;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        // 방어적 복사 (Defensive Copy) 적용으로 100% 불변 리스트 보장 및 외부 변형 차단
        this.resortNames = resortNames != null ? List.copyOf(resortNames) : List.of();
        this.ridingStyleNames =
                ridingStyleNames != null ? List.copyOf(ridingStyleNames) : List.of();
    }

    public static MemberLoginResponse from(
            Member member, List<String> resortNames, List<String> ridingStyleNames) {
        return MemberLoginResponse.builder()
                .publicId(member.getPublicId())
                .email(member.getEmail())
                .nickname(member.getNickname())
                .role(member.getRole())
                .resortNames(resortNames)
                .ridingStyleNames(ridingStyleNames)
                .build();
    }
}
