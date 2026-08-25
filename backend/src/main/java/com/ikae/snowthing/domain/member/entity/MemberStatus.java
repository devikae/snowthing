package com.ikae.snowthing.domain.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberStatus {

    ACTIVE("정상 활성 계정"),
    SUSPENDED("제재/정지 계정"),
    WITHDRAWN("탈퇴 계정");

    private final String description;
}
