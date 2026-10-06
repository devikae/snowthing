package com.ikae.snowthing.domain.email.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ikae.snowthing.domain.email.dto.*;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.service.EmailVerificationService;
import com.ikae.snowthing.domain.email.service.PasswordResetService;
import com.ikae.snowthing.global.security.MemberSessionRegistry;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private static final String PASSWORD_RESET_RESPONSE = "입력한 이메일로 안내를 전송했습니다.";

    private final EmailVerificationService verificationService;
    private final PasswordResetService passwordResetService;
    private final MemberSessionRegistry memberSessionRegistry;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/email-verifications/sign-up")
    public ResponseEntity<EmailVerificationSendResponse> requestSignUpCode(
            @Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(
                verificationService.requestSignUp(
                        request.email(), clientIpResolver.resolve(httpRequest)));
    }

    @PostMapping("/email-verifications/sign-up/confirm")
    public ResponseEntity<SignUpVerificationTokenResponse> confirmSignUpCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        EmailVerificationTokenResponse response =
                verificationService.confirm(
                        request.requestId(), request.code(), EmailVerificationPurpose.SIGN_UP);
        return ResponseEntity.ok(
                new SignUpVerificationTokenResponse(response.token(), response.expiresInSeconds()));
    }

    @PostMapping("/password-reset/requests")
    public ResponseEntity<MessageResponse> requestPasswordReset(
            @Valid @RequestBody EmailRequest request, HttpServletRequest httpRequest) {
        EmailVerificationSendResponse response =
                verificationService.requestPasswordReset(
                        request.email(), clientIpResolver.resolve(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new MessageResponse(PASSWORD_RESET_RESPONSE, response.requestId()));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<PasswordResetTokenResponse> confirmPasswordReset(
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        EmailVerificationTokenResponse response =
                verificationService.confirm(
                        request.requestId(),
                        request.code(),
                        EmailVerificationPurpose.PASSWORD_RESET);
        return ResponseEntity.ok(
                new PasswordResetTokenResponse(response.token(), response.expiresInSeconds()));
    }

    @PutMapping("/password-reset")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        String memberPublicId =
                passwordResetService.reset(request.resetToken(), request.newPassword());
        memberSessionRegistry.invalidateAll(memberPublicId);
        return ResponseEntity.noContent().build();
    }
}
