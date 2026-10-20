package com.ikae.snowthing.global.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.ikae.snowthing.domain.member.entity.Member;

public class BotDelegatedUserDetails extends CustomUserDetails {

    private static final List<GrantedAuthority> AUTHORITIES =
            List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("SCOPE_snowthing:read"));

    public BotDelegatedUserDetails(Member member) {
        super(member);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }
}
