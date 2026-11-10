package com.ikae.snowthing.domain.member.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ikae.snowthing.domain.member.entity.SocialProvider;

/** 내 정보 수정 화면의 로그인 방법 목록. passwordLogin은 이메일과 비밀번호로도 로그인할 수 있는지 알려 준다. */
public record SocialAccountsResponse(boolean passwordLogin, List<LinkedAccount> accounts) {

    public SocialAccountsResponse {
        accounts = List.copyOf(accounts);
    }

    public record LinkedAccount(SocialProvider provider, LocalDateTime connectedAt) {}
}
