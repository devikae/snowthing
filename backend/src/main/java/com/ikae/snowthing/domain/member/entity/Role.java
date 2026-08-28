package com.ikae.snowthing.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    GUEST("ROLE_GUEST", "비회원 유저"),
    ROLE_USER("ROLE_USER", "일반 회원"),
    ROLE_ADMIN("ROLE_ADMIN", "서비스 관리자");

    private final String key;
    private final String title;
}
