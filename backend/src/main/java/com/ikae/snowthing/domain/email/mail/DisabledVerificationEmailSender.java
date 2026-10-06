package com.ikae.snowthing.domain.email.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@Component
@ConditionalOnProperty(
        name = "snowthing.mail.provider",
        havingValue = "disabled",
        matchIfMissing = true)
public class DisabledVerificationEmailSender implements VerificationEmailSender {

    @Override
    public String sendVerificationCode(
            String recipient, String verificationCode, EmailVerificationPurpose purpose) {
        throw new CustomException(ErrorCode.EMAIL_DELIVERY_UNAVAILABLE);
    }
}
