package com.ikae.snowthing.domain.member.dto;

import com.ikae.snowthing.domain.member.entity.Member;

/** 쪽지 받는 회원 검색 결과. 게시글에 이미 공개된 값만 담는다. */
public record MemberSearchResponse(String publicId, String nickname, String profileImageUrl) {

    public static MemberSearchResponse from(Member member) {
        return new MemberSearchResponse(
                member.getPublicId(), member.getNickname(), member.getProfileImageUrl());
    }
}
