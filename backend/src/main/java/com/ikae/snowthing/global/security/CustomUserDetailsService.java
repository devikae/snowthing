package com.ikae.snowthing.global.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final EmailNormalizer emailNormalizer;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member member =
                memberRepository
                        .findByEmail(emailNormalizer.normalize(email))
                        .orElseThrow(
                                () ->
                                        new UsernameNotFoundException(
                                                ErrorCode.MEMBER_NOT_FOUND.getMessage()));
        return new CustomUserDetails(member);
    }
}
