package com.ikae.snowthing.domain.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailRequest(
        @NotBlank(message = "이메일은 필수 입력값입니다.")
                @Email(message = "올바른 이메일 형식이어야 합니다.")
                @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
                String email) {}
