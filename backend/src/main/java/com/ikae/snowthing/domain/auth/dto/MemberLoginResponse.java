package com.ikae.snowthing.domain.auth.dto;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class MemberLoginResponse {

    private final String publicId;
    private final String email;
    private final String nickname;
    private final Role role;
    private final List<String> resortNames;
    private final List<String> ridingStyleNames;

    @Builder
    public MemberLoginResponse(String publicId, String email, String nickname, Role role, List<String> resortNames, List<String> ridingStyleNames) {
        this.publicId = publicId;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.resortNames = resortNames != null ? resortNames : new ArrayList<>();
        this.ridingStyleNames = ridingStyleNames != null ? ridingStyleNames : new ArrayList<>();
    }

    public static MemberLoginResponse from(Member member, List<String> resortNames, List<String> ridingStyleNames) {
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
