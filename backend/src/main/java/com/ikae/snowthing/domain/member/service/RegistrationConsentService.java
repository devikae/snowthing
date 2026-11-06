package com.ikae.snowthing.domain.member.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.member.dto.RegistrationConsentRequest;
import com.ikae.snowthing.domain.member.entity.MemberRegistrationConsent;
import com.ikae.snowthing.domain.member.repository.MemberRegistrationConsentRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/** 이메일 가입과 소셜 가입이 같은 기준으로 동의를 검증하고 기록한다. */
@Service
@RequiredArgsConstructor
public class RegistrationConsentService {

    private final MemberRegistrationConsentRepository consentRepository;
    private final RegistrationPrivacyPolicy policy;

    public void validate(RegistrationConsentRequest request) {
        if (!Boolean.TRUE.equals(request.getAgeConfirmed())
                || !Boolean.TRUE.equals(request.getPrivacyAgreed())
                || !Boolean.TRUE.equals(request.getTermsAgreed())
                || request.getMarketingEmailAgreed() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        policy.validate(request.getPrivacyVersion(), request.getTermsVersion());
    }

    /** 저장하는 버전은 클라이언트 값이 아니라 서버의 현재 문서 버전이다. */
    public void record(Long memberId, RegistrationConsentRequest request, LocalDateTime now) {
        consentRepository.save(
                new MemberRegistrationConsent(
                        memberId,
                        policy.version(),
                        policy.termsVersion(),
                        Boolean.TRUE.equals(request.getMarketingEmailAgreed()),
                        now));
    }
}
