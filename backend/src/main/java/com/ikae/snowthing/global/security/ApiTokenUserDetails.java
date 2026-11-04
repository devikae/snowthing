package com.ikae.snowthing.global.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;

import com.ikae.snowthing.domain.apitoken.entity.ApiTokenScope;
import com.ikae.snowthing.domain.member.entity.Member;

import lombok.Getter;

/**
 * API 토큰으로 인증한 회원. 소유자가 관리자여도 ROLE_ADMIN을 주지 않고 토큰 범위 권한만 준다. 그래서 {@code /api/v1/admin/**} 중
 * SecurityConfig가 범위 권한으로 열어 둔 경로 밖으로는 나갈 수 없고, 승인과 게시는 어떤 범위로도 할 수 없다.
 */
@Getter
public class ApiTokenUserDetails extends CustomUserDetails {

    private final Long tokenId;
    private final Set<ApiTokenScope> scopes;
    private final List<GrantedAuthority> authorities;

    public ApiTokenUserDetails(Member member, Long tokenId, Set<ApiTokenScope> scopes) {
        super(member);
        this.tokenId = tokenId;
        this.scopes = Set.copyOf(scopes);
        this.authorities = scopes.stream().map(ApiTokenScope::grantedAuthority).toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }
}
