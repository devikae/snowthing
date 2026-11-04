package com.ikae.snowthing.domain.apitoken.entity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * API 토큰 범위. 게시(PUBLISH)는 의도적으로 없다. 승인과 게시는 사람이 관리자 세션으로만 한다.
 *
 * <p>권한 이름은 라이브톡 위임 토큰의 {@code SCOPE_snowthing:read}와 겹치지 않게 따로 둔다. 위임 토큰이 관리자 근거 API에 들어오지
 * 못하게 하기 위해서다.
 */
@Getter
@RequiredArgsConstructor
public enum ApiTokenScope {
    READ("API_TOKEN_READ"),
    DRAFT("API_TOKEN_DRAFT");

    private final String authority;

    public GrantedAuthority grantedAuthority() {
        return new SimpleGrantedAuthority(authority);
    }
}
