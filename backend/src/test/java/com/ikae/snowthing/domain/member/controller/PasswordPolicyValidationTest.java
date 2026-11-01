package com.ikae.snowthing.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.email.dto.PasswordResetRequest;
import com.ikae.snowthing.domain.member.dto.MemberPasswordChangeRequest;
import com.ikae.snowthing.domain.member.dto.MemberSignUpRequest;

class PasswordPolicyValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void signupAndResetApplyTheSameLengthPolicy() {
        for (String password :
                new String[] {
                    "1234567",
                    "12345678",
                    "12345678901234567890",
                    "123456789012345678901",
                    "        "
                }) {
            boolean valid =
                    password.length() >= 8 && password.length() <= 20 && !password.isBlank();
            var signup = MemberSignUpRequest.builder().password(password).build();
            var reset = new PasswordResetRequest("token", password);
            var change = new MemberPasswordChangeRequest("current", password);
            assertThat(validator.validateProperty(signup, "password").isEmpty())
                    .as("signup length %s", password.length())
                    .isEqualTo(valid);
            assertThat(validator.validateProperty(reset, "newPassword").isEmpty())
                    .as("reset length %s", password.length())
                    .isEqualTo(valid);
            assertThat(validator.validateProperty(change, "newPassword").isEmpty())
                    .as("change length %s", password.length())
                    .isEqualTo(valid);
        }
    }
}
