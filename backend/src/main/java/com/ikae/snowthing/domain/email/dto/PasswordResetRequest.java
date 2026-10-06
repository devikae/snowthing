package com.ikae.snowthing.domain.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank String resetToken,
        @NotBlank
                @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다.")
                @Pattern(regexp = ".*[A-Z].*", message = "비밀번호에는 영문 대문자가 포함되어야 합니다.")
                @Pattern(
                        regexp = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*",
                        message = "비밀번호에는 특수문자가 포함되어야 합니다.")
                String newPassword) {}
