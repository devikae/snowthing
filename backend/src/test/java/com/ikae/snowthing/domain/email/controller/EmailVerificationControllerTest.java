package com.ikae.snowthing.domain.email.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.ikae.snowthing.domain.email.dto.*;
import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.domain.email.service.EmailVerificationService;
import com.ikae.snowthing.domain.email.service.PasswordResetService;
import com.ikae.snowthing.global.security.MemberSessionRegistry;
import com.ikae.snowthing.global.web.ClientIpResolver;

class EmailVerificationControllerTest {

    private EmailVerificationService verificationService;
    private PasswordResetService passwordResetService;
    private MemberSessionRegistry memberSessionRegistry;
    private EmailVerificationController controller;

    @BeforeEach
    void setUp() {
        verificationService = mock(EmailVerificationService.class);
        passwordResetService = mock(PasswordResetService.class);
        memberSessionRegistry = mock(MemberSessionRegistry.class);
        controller =
                new EmailVerificationController(
                        verificationService,
                        passwordResetService,
                        memberSessionRegistry,
                        mock(ClientIpResolver.class));
    }

    @Test
    void signUpConfirmationUsesVerificationTokenContract() {
        given(verificationService.confirm("request-id", "012345", EmailVerificationPurpose.SIGN_UP))
                .willReturn(new EmailVerificationTokenResponse("opaque-token", 900));

        SignUpVerificationTokenResponse body =
                controller
                        .confirmSignUpCode(
                                new EmailVerificationConfirmRequest("request-id", "012345"))
                        .getBody();

        assertThat(body).isNotNull();
        assertThat(body.verificationToken()).isEqualTo("opaque-token");
        assertThat(body.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void passwordResetConfirmationUsesResetTokenContract() {
        given(
                        verificationService.confirm(
                                "request-id", "012345", EmailVerificationPurpose.PASSWORD_RESET))
                .willReturn(new EmailVerificationTokenResponse("opaque-token", 900));

        PasswordResetTokenResponse body =
                controller
                        .confirmPasswordReset(
                                new EmailVerificationConfirmRequest("request-id", "012345"))
                        .getBody();

        assertThat(body).isNotNull();
        assertThat(body.resetToken()).isEqualTo("opaque-token");
    }

    @Test
    void passwordResetRequestAlwaysReturnsAcceptedPublicMessage() {
        given(verificationService.requestPasswordReset(eq("unknown@example.com"), any()))
                .willReturn(new EmailVerificationSendResponse("opaque-request", 300, 60));

        var response =
                controller.requestPasswordReset(
                        new EmailRequest("unknown@example.com"), mock(HttpServletRequest.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("입력한 이메일로 안내를 전송했습니다.");
    }

    @Test
    void successfulPasswordResetInvalidatesSessionsAfterServiceReturns() {
        PasswordResetRequest request = new PasswordResetRequest("reset-token", "NewPassword1!");
        given(passwordResetService.reset("reset-token", "NewPassword1!"))
                .willReturn("member-public-id");

        assertThat(controller.resetPassword(request).getStatusCode().value()).isEqualTo(204);
        verify(memberSessionRegistry).invalidateAll("member-public-id");
    }
}
