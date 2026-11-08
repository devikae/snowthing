package com.ikae.snowthing.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.event.MemberSignedUpEvent;
import com.ikae.snowthing.domain.member.repository.MemberRepository;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private WithdrawnEmailService withdrawnEmailService;

    @Mock private PasswordEncoder passwordEncoder;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Spy private EmailNormalizer emailNormalizer = new EmailNormalizer();

    @InjectMocks private MemberService memberService;

    @Test
    @DisplayName("올바른 정보 입력 시 회원가입이 정상 처리되어야 한다")
    void signUp_Success() {
        // given
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
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
        verify(eventPublisher).publishEvent(new MemberSignedUpEvent("신규보더", "EMAIL"));
    }

    @Test
    @DisplayName("이미 존재하는 이메일로 가입 시 예외가 발생해야 한다")
    void signUp_DuplicateEmail_Exception() {
        // given
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("exist@snowthing.com")
                        .password("Password123!")
                        .nickname("닉네임1")
                        .build();

        given(memberRepository.existsByEmail("exist@snowthing.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(com.ikae.snowthing.global.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.ikae.snowthing.global.error.ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("운영자나 관리자가 들어간 닉네임은 가입할 수 없다")
    void signUp_ReservedNickname_Exception() {
        for (String nickname : java.util.List.of("운영자", "눈팅관리자1")) {
            MemberSignUpRequest request =
                    MemberSignUpRequest.builder()
                            .email("new@snowthing.com")
                            .password("Password123!")
                            .nickname(nickname)
                            .build();

            assertThatThrownBy(() -> memberService.signUp(request))
                    .isInstanceOf(com.ikae.snowthing.global.exception.CustomAuthException.class)
                    .extracting("errorCode")
                    .isEqualTo(com.ikae.snowthing.global.error.ErrorCode.RESERVED_NICKNAME);
        }
    }

    @Test
    @DisplayName("이미 존재하는 닉네임으로 가입 시 예외가 발생해야 한다")
    void signUp_DuplicateNickname_Exception() {
        // given
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
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

    @Test
    @DisplayName("동시 가입의 닉네임 unique 위반도 중복 닉네임 오류로 변환해야 한다")
    void signUp_ConcurrentDuplicateNickname_Exception() {
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("new@snowthing.com")
                        .password("Password123!")
                        .nickname("동시가입닉네임")
                        .build();

        given(memberRepository.existsByEmail("new@snowthing.com")).willReturn(false);
        given(memberRepository.existsByNickname("동시가입닉네임")).willReturn(false);
        given(passwordEncoder.encode("Password123!")).willReturn("bcrypted_password_123");
        DataIntegrityViolationException violation =
                constraintViolation(Member.NICKNAME_UNIQUE_CONSTRAINT);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(violation);

        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(com.ikae.snowthing.global.exception.CustomAuthException.class)
                .extracting("errorCode")
                .isEqualTo(com.ikae.snowthing.global.error.ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    @DisplayName("동시 가입의 이메일 unique 위반은 사전 검사와 같은 이미 가입된 이메일 오류로 변환해야 한다")
    void signUp_ConcurrentDuplicateEmail_Exception() {
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("race@snowthing.com")
                        .password("Password123!")
                        .nickname("이메일경쟁")
                        .build();

        given(memberRepository.existsByEmail("race@snowthing.com")).willReturn(false);
        given(memberRepository.existsByNickname("이메일경쟁")).willReturn(false);
        given(passwordEncoder.encode("Password123!")).willReturn("bcrypted_password_123");
        // MySQL은 "테이블.제약" 형태로 이름을 돌려준다
        DataIntegrityViolationException violation =
                constraintViolation("member." + Member.EMAIL_UNIQUE_CONSTRAINT);
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(violation);

        assertThatThrownBy(() -> memberService.signUp(request))
                .isInstanceOf(com.ikae.snowthing.global.exception.CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.ikae.snowthing.global.error.ErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("회원 unique 제약이 아닌 무결성 위반은 바꾸지 않고 그대로 던져야 한다")
    void signUp_OtherIntegrityViolation_Rethrown() {
        MemberSignUpRequest request =
                MemberSignUpRequest.builder()
                        .email("other@snowthing.com")
                        .password("Password123!")
                        .nickname("다른제약")
                        .build();
        DataIntegrityViolationException violation = constraintViolation("uk_member_public_id");

        given(memberRepository.existsByEmail("other@snowthing.com")).willReturn(false);
        given(memberRepository.existsByNickname("다른제약")).willReturn(false);
        given(passwordEncoder.encode("Password123!")).willReturn("bcrypted_password_123");
        given(memberRepository.saveAndFlush(any(Member.class))).willThrow(violation);

        assertThatThrownBy(() -> memberService.signUp(request)).isSameAs(violation);
    }

    private DataIntegrityViolationException constraintViolation(String constraintName) {
        ConstraintViolationException cause = mock(ConstraintViolationException.class);
        given(cause.getConstraintName()).willReturn(constraintName);
        return new DataIntegrityViolationException("constraint violation", cause);
    }
}
