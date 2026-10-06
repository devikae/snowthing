package com.ikae.snowthing.domain.email.mail;

import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;

public interface VerificationEmailSender {
    String sendVerificationCode(
            String recipient, String verificationCode, EmailVerificationPurpose purpose);
}
