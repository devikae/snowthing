package com.ikae.snowthing.domain.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.auth.dto.MemberLoginResponse;
import com.ikae.snowthing.domain.member.entity.MemberStatus;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.service.EmailNormalizer;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomAuthException;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final EmailNormalizer emailNormalizer;

    /** 프로필 정보 및 선호 스키장/라이딩 성향 QueryDSL DTO 프로젝션 조회 (방어적 복사 적용) */
    public MemberLoginResponse getMemberProfileByEmail(String email) {
        return memberRepository
                .findProfileByEmail(emailNormalizer.normalize(email))
                .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 세션을 등록한 뒤 상태를 다시 읽는다. 인증과 등록 사이에 정지가 끼어들면 무효화할 때 이 세션이 아직 목록에 없어 살아남기 때문이다. 정지는 커밋 뒤 무효화하므로
     * 등록 후 읽으면 둘 중 하나는 반드시 이 세션을 잡는다.
     */
    public void ensureStillActive(String publicId) {
        MemberStatus status =
                memberRepository
                        .findByPublicId(publicId)
                        .orElseThrow(() -> new CustomAuthException(ErrorCode.MEMBER_NOT_FOUND))
                        .getStatus();
        if (status == MemberStatus.SUSPENDED) {
            throw new CustomException(ErrorCode.ACCOUNT_SUSPENDED);
        }
        if (status == MemberStatus.WITHDRAWN) {
            throw new CustomException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
    }
}
