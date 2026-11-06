package com.ikae.snowthing.domain.member.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.member.dto.MarketingConsentResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberMarketingConsentHistory;
import com.ikae.snowthing.domain.member.repository.MemberMarketingConsentHistoryRepository;
import com.ikae.snowthing.domain.member.repository.MemberRegistrationConsentRepository;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.RequiredArgsConstructor;

/**
 * 현재 수신 동의는 가장 최근 변경 이력이고, 이력이 없으면 가입 때 값, 그것도 없으면 미동의다. 가입 때 기록은 당시 사실이므로 바꾸지 않고, 변경은 이력에 추가만 한다.
 */
@Service
@RequiredArgsConstructor
public class MarketingConsentService {

    private final MemberRepository memberRepository;
    private final MemberRegistrationConsentRepository registrationConsentRepository;
    private final MemberMarketingConsentHistoryRepository historyRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MarketingConsentResponse get(String email) {
        return current(findMember(email).getId());
    }

    @Transactional
    public MarketingConsentResponse update(String email, boolean agreed) {
        Long memberId = findMember(email).getId();
        MarketingConsentResponse current = current(memberId);
        if (current.agreed() == agreed && current.changedAt() != null) {
            return current;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        historyRepository.save(new MemberMarketingConsentHistory(memberId, agreed, now));
        return new MarketingConsentResponse(agreed, now);
    }

    private MarketingConsentResponse current(Long memberId) {
        return historyRepository
                .findFirstByMemberIdOrderByIdDesc(memberId)
                .map(
                        history ->
                                new MarketingConsentResponse(
                                        history.isAgreed(), history.getChangedAt()))
                .or(
                        () ->
                                registrationConsentRepository
                                        .findById(memberId)
                                        .map(
                                                consent ->
                                                        new MarketingConsentResponse(
                                                                consent.isMarketingEmailAgreed(),
                                                                consent.getConfirmedAt())))
                .orElseGet(() -> new MarketingConsentResponse(false, null));
    }

    private Member findMember(String email) {
        return memberRepository
                .findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
