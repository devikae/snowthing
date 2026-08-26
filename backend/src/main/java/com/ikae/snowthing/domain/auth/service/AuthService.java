package com.ikae.snowthing.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;

    /** 프로필 정보 및 선호 스키장/라이딩 성향 QueryDSL DTO 프로젝션 조회 (방어적 복사 적용) */
    public MemberLoginResponse getMemberProfileByEmail(String email) {
        return memberRepository
                .findProfileByEmail(email)
                .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
