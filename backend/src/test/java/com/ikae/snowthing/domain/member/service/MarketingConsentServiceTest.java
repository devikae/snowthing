package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ikae.snowthing.domain.member.dto.MarketingConsentResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.MemberMarketingConsentHistory;
import com.ikae.snowthing.domain.member.entity.MemberRegistrationConsent;
import com.ikae.snowthing.domain.member.repository.MemberMarketingConsentHistoryRepository;
import com.ikae.snowthing.domain.member.repository.MemberRegistrationConsentRepository;
import com.ikae.snowthing.domain.member.repository.MemberRepository;

class MarketingConsentServiceTest {

    private static final String EMAIL = "member@snowthing.org";
    private static final Long MEMBER_ID = 7L;
    private static final LocalDateTime SIGNED_UP = LocalDateTime.of(2026, 1, 1, 9, 0);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-06T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    private MemberRegistrationConsentRepository registrationRepository;
    private MemberMarketingConsentHistoryRepository historyRepository;
    private MarketingConsentService service;

    @BeforeEach
    void setUp() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        Member member = mock(Member.class);
        given(member.getId()).willReturn(MEMBER_ID);
        given(memberRepository.findByEmail(EMAIL)).willReturn(Optional.of(member));
        registrationRepository = mock(MemberRegistrationConsentRepository.class);
        historyRepository = mock(MemberMarketingConsentHistoryRepository.class);
        given(historyRepository.findFirstByMemberIdOrderByIdDesc(MEMBER_ID))
                .willReturn(Optional.empty());
        given(registrationRepository.findById(MEMBER_ID)).willReturn(Optional.empty());
        service =
                new MarketingConsentService(
                        memberRepository, registrationRepository, historyRepository, CLOCK);
    }

    @Test
    @DisplayName("동의 기록이 없는 기존 회원은 미동의로 처리한다")
    void get_NoRecord_IsNotAgreed() {
        assertThat(service.get(EMAIL)).isEqualTo(new MarketingConsentResponse(false, null));
    }

    @Test
    @DisplayName("변경 이력이 없으면 가입 때 값을 쓴다")
    void get_NoHistory_UsesRegistrationValue() {
        given(registrationRepository.findById(MEMBER_ID))
                .willReturn(
                        Optional.of(
                                new MemberRegistrationConsent(
                                        MEMBER_ID, "privacy", "terms", true, SIGNED_UP)));

        assertThat(service.get(EMAIL)).isEqualTo(new MarketingConsentResponse(true, SIGNED_UP));
    }

    @Test
    @DisplayName("변경 이력이 가입 때 값보다 우선한다")
    void get_History_OverridesRegistrationValue() {
        LocalDateTime changed = SIGNED_UP.plusDays(1);
        given(historyRepository.findFirstByMemberIdOrderByIdDesc(MEMBER_ID))
                .willReturn(
                        Optional.of(new MemberMarketingConsentHistory(MEMBER_ID, false, changed)));

        assertThat(service.get(EMAIL)).isEqualTo(new MarketingConsentResponse(false, changed));
    }

    @Test
    @DisplayName("값을 바꾸면 처리 시각과 함께 이력을 남긴다")
    void update_Changed_AppendsHistory() {
        LocalDateTime now = LocalDateTime.now(CLOCK);

        assertThat(service.update(EMAIL, true)).isEqualTo(new MarketingConsentResponse(true, now));

        ArgumentCaptor<MemberMarketingConsentHistory> saved =
                ArgumentCaptor.forClass(MemberMarketingConsentHistory.class);
        verify(historyRepository).save(saved.capture());
        assertThat(saved.getValue().getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getValue().isAgreed()).isTrue();
    }

    @Test
    @DisplayName("같은 값으로 다시 저장하면 이력을 늘리지 않는다")
    void update_SameValue_DoesNotAppend() {
        given(historyRepository.findFirstByMemberIdOrderByIdDesc(MEMBER_ID))
                .willReturn(
                        Optional.of(new MemberMarketingConsentHistory(MEMBER_ID, true, SIGNED_UP)));

        assertThat(service.update(EMAIL, true).changedAt()).isEqualTo(SIGNED_UP);

        verify(historyRepository, never()).save(any());
    }
}
