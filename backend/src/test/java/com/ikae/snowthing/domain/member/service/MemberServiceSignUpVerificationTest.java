package com.ikae.snowthing.domain.member.service;

import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpResponse;
import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.repository.MemberRepository;
import com.ikae.snowthing.domain.member.repository.MemberResortRepository;
import com.ikae.snowthing.domain.member.repository.MemberRidingStyleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MemberServiceSignUpVerificationTest {

    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberResortRepository memberResortRepository;
    @Autowired private MemberRidingStyleRepository memberRidingStyleRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        memberResortRepository.deleteAll();
        memberRidingStyleRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("[검증 1] 저장된 비밀번호는 $2a$ 로 시작하는 해시 포맷이어야 한다 (raw 비번 아님)")
    void signUp_SavesHashedPassword() {
        MemberSignUpRequest signUpRequest = MemberSignUpRequest.builder()
                .email("verify1@snowthing.com")
                .password("Password123!")
                .nickname("해시테스터")
                .build();

        memberService.signUp(signUpRequest);

        Member savedMember = memberRepository.findByEmail("verify1@snowthing.com").orElseThrow();
        assertThat(savedMember.getPassword()).startsWith("$2a$");
        assertThat(savedMember.getPassword()).isNotEqualTo("Password123!");
    }

    @Test
    @DisplayName("[검증 2] 회원가입 응답 DTO(MemberSignUpResponse) 에는 internal id 필드가 포함되지 않아야 한다")
    void signUp_ResponseDtoHasNoInternalId() {
        MemberSignUpRequest signUpRequest = MemberSignUpRequest.builder()
                .email("verify2@snowthing.com")
                .password("Password123!")
                .nickname("DTO테스터")
                .build();

        MemberSignUpResponse response = memberService.signUp(signUpRequest);

        Field[] fields = MemberSignUpResponse.class.getDeclaredFields();
        boolean hasInternalIdField = false;
        for (Field field : fields) {
            if ("id".equals(field.getName()) || "memberId".equals(field.getName())) {
                hasInternalIdField = true;
                break;
            }
        }
        assertThat(hasInternalIdField).isFalse();
        assertThat(response.getPublicId()).isNotNull();
    }
}
