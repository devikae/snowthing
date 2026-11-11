package com.ikae.snowthing.domain.auth.dto;

import jakarta.validation.constraints.Size;

/** 비밀번호가 있는 회원은 소셜 연결을 시작하기 전에 현재 비밀번호를 다시 확인한다. 소셜 전용 회원은 비워 보낸다. */
public record SocialLinkStartRequest(@Size(max = 200) String currentPassword) {}
