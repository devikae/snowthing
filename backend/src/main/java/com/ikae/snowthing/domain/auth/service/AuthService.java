package com.ikae.snowthing.domain.auth.service;

import com.ikae.snowthing.domain.auth.dto.MemberLoginRequest;
import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberResortRepository;
import com.ikae.snowthing.domain.member.repository.MemberRidingStyleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final MemberResortRepository memberResortRepository;
    private final MemberRidingStyleRepository memberRidingStyleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberLoginResponse login(MemberLoginRequest request, HttpServletRequest httpRequest) {
        Member member = memberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("INVALID_CREDENTIALS"));

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new IllegalArgumentException("INVALID_CREDENTIALS");
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                member.getEmail(),
                null,
                Collections.singletonList(new SimpleGrantedAuthority(member.getRole().getKey()))
        );

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = httpRequest.getSession(true);
        httpRequest.changeSessionId();

        int sessionTimeoutSeconds = request.isRememberMe() ? 30 * 24 * 60 * 60 : 60 * 60;
        session.setMaxInactiveInterval(sessionTimeoutSeconds);
        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);

        List<String> resortNames = memberResortRepository.findAllByMemberIdWithResort(member.getId()).stream()
                .map(mr -> mr.getResort().getName())
                .toList();

        List<String> ridingStyleNames = memberRidingStyleRepository.findAllByMemberIdWithRidingStyle(member.getId()).stream()
                .map(mrs -> mrs.getRidingStyle().getStyleName())
                .toList();

        return MemberLoginResponse.from(member, resortNames, ridingStyleNames);
    }

    public void logout(HttpServletRequest httpRequest) {
        SecurityContextHolder.clearContext();

        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    public MemberLoginResponse getMyProfile(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("MEMBER_NOT_FOUND"));

        List<String> resortNames = memberResortRepository.findAllByMemberIdWithResort(member.getId()).stream()
                .map(mr -> mr.getResort().getName())
                .toList();

        List<String> ridingStyleNames = memberRidingStyleRepository.findAllByMemberIdWithRidingStyle(member.getId()).stream()
                .map(mrs -> mrs.getRidingStyle().getStyleName())
                .toList();

        return MemberLoginResponse.from(member, resortNames, ridingStyleNames);
    }
}
