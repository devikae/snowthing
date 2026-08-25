package com.ikae.snowthing.domain.auth.service;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberResortRepository;
import com.ikae.snowthing.domain.member.repository.MemberRidingStyleRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final MemberResortRepository memberResortRepository;
    private final MemberRidingStyleRepository memberRidingStyleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 회원 자격 증명 (이메일 및 비밀번호 검증)
     */
    public Member authenticate(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomAuthException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new CustomAuthException(ErrorCode.INVALID_CREDENTIALS);
        }

        return member;
    }

    /**
     * 프로필 정보 및 선호 스키장/라이딩 성향 Fetch Join 조회 (방어적 복사 적용)
     */
    public MemberLoginResponse getMemberProfileByEmail(String email) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));

        List<String> resortNames = memberResortRepository.findAllByMemberIdWithResort(member.getId()).stream()
                .map(mr -> mr.getResort().getName())
                .toList();

        List<String> ridingStyleNames = memberRidingStyleRepository.findAllByMemberIdWithRidingStyle(member.getId()).stream()
                .map(mrs -> mrs.getRidingStyle().getStyleName())
                .toList();

        return MemberLoginResponse.from(member, resortNames, ridingStyleNames);
    }
}
