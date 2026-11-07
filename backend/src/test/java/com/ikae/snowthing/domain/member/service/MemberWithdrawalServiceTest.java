package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ikae.snowthing.domain.aimember.AiMemberRoster;
import com.ikae.snowthing.domain.image.service.ProfileImageLifecycleService;
import com.ikae.snowthing.domain.member.dto.MemberWithdrawalRequest;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberSocialAccountRepository;
import com.ikae.snowthing.domain.member.repository.MemberWithdrawalRepository;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class MemberWithdrawalServiceTest {

    @Test
    @DisplayName("AI 멤버 명단에 있는 계정은 비밀번호가 맞아도 탈퇴할 수 없다")
    @SuppressWarnings("unchecked")
    void aiMemberCannotWithdraw() {
        MemberRepository members = mock(MemberRepository.class);
        MemberWithdrawalRepository erasure = mock(MemberWithdrawalRepository.class);
        ObjectProvider<AiMemberRoster> provider = mock(ObjectProvider.class);
        AiMemberRoster roster = mock(AiMemberRoster.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Member member = Member.builder().email("ai@example.com").password("hash").build();
        ReflectionTestUtils.setField(member, "id", 7L);
        given(members.findByIdForUpdate(7L)).willReturn(Optional.of(member));
        given(provider.getIfAvailable()).willReturn(roster);
        given(roster.memberIds()).willReturn(Set.of(7L));

        MemberWithdrawalService service =
                new MemberWithdrawalService(
                        members,
                        mock(MemberSocialAccountRepository.class),
                        erasure,
                        mock(WithdrawnEmailService.class),
                        mock(ProfileImageLifecycleService.class),
                        provider,
                        encoder,
                        new EmailNormalizer(),
                        Clock.systemUTC());

        assertThatThrownBy(() -> service.withdraw(7L, new MemberWithdrawalRequest("pw", null)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verifyNoInteractions(erasure);
    }
}
