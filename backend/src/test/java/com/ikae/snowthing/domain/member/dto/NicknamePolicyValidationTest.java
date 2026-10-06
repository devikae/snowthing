package com.ikae.snowthing.domain.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NicknamePolicyValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("회원가입과 프로필 수정은 10자 닉네임을 허용한다")
    void acceptsTenCharacterNickname() {
        String nickname = "가".repeat(10);

        assertThat(validator.validate(signUpRequest(nickname)))
                .noneMatch(violation -> violation.getPropertyPath().toString().equals("nickname"));
        assertThat(validator.validate(profileUpdateRequest(nickname)))
                .noneMatch(violation -> violation.getPropertyPath().toString().equals("nickname"));
    }

    @Test
    @DisplayName("회원가입과 프로필 수정은 11자 닉네임을 거절한다")
    void rejectsElevenCharacterNickname() {
        String nickname = "가".repeat(11);

        assertThat(validator.validate(signUpRequest(nickname)))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("nickname"));
        assertThat(validator.validate(profileUpdateRequest(nickname)))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("nickname"));
    }

    private MemberSignUpRequest signUpRequest(String nickname) {
        return MemberSignUpRequest.builder()
                .email("nickname@snowthing.org")
                .password("Password123!")
                .emailVerificationToken("verification-token")
                .nickname(nickname)
                .build();
    }

    private MemberProfileUpdateRequest profileUpdateRequest(String nickname) {
        return MemberProfileUpdateRequest.builder().nickname(nickname).build();
    }
}
