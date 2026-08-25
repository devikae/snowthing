package com.ikae.snowthing.domain.member.service;

import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    @Test
    @DisplayName("올바른 정보 입력 시 회원가입이 정상 처리되어야 한다")
    void signUp_Success() {
        // given
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("newuser@snowthing.com")
                .password("Password123!")
                .nickname("신규보더")
                .bio("입문 보더입니다")
                .departureRegion("서울 송파구")
                .build();

        given(memberRepository.existsByEmail("newuser@snowthing.com")).willReturn(false);
        given(memberRepository.existsByNickname("신규보더")).willReturn(false);
        given(passwordEncoder.encode("Password123!")).willReturn("bcrypted_password_123");

        Member member = request.toEntity("bcrypted_password_123");
        given(memberRepository.saveAndFlush(any(Member.class))).willReturn(member);

        // when
        MemberSignUpResponse response = memberService.signUp(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("newuser@snowthing.com");
        assertThat(response.getNickname()).isEqualTo("신규보더");
        verify(memberRepository).saveAndFlush(any(Member.class));
    }

    @Test
    @DisplayName("이미 존재하는 이메일로 가입 시 예외가 발생해야 한다")
    void signUp_DuplicateEmail_Exception() {
        // given
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("exist@snowthing.com")
                .password("Password123!")
                .nickname("닉네임1")
                .build();

        given(memberRepository.existsByEmail("exist@snowthing.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(com.ikae.snowthing.global.exception.CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(com.ikae.snowthing.global.error.ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("이미 존재하는 닉네임으로 가입 시 예외가 발생해야 한다")
    void signUp_DuplicateNickname_Exception() {
        // given
        MemberSignUpRequest request = MemberSignUpRequest.builder()
                .email("new@snowthing.com")
                .password("Password123!")
                .nickname("중복닉네임")
                .build();

        given(memberRepository.existsByEmail("new@snowthing.com")).willReturn(false);
        given(memberRepository.existsByNickname("중복닉네임")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(com.ikae.snowthing.global.exception.CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(com.ikae.snowthing.global.error.ErrorCode.DUPLICATE_NICKNAME);
    }
}
