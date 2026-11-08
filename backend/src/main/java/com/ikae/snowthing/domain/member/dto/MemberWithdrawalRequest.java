package com.ikae.snowthing.domain.member.dto;

import jakarta.validation.constraints.Size;

/** 비밀번호 회원은 password, 소셜 회원은 confirmation에 {@code 탈퇴}를 담아 보낸다. */
public record MemberWithdrawalRequest(
        @Size(max = 200) String password, @Size(max = 20) String confirmation) {}
