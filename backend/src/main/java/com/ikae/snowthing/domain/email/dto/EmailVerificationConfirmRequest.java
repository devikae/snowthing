package com.ikae.snowthing.domain.email.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailVerificationConfirmRequest(
        @NotBlank(message = "인증 요청 ID는 필수입니다.") String requestId, String code) {}
