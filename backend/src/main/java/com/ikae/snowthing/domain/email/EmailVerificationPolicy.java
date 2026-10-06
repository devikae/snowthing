package com.ikae.snowthing.domain.email;

import java.time.Duration;

public final class EmailVerificationPolicy {

    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    public static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    public static final Duration RESEND_DELAY = Duration.ofSeconds(60);
    public static final Duration SEND_WINDOW = Duration.ofHours(1);
    public static final int MAX_SENDS_PER_EMAIL = 5;
    public static final int MAX_SENDS_PER_IP = 20;
    public static final Duration AVAILABILITY_CHECK_WINDOW = Duration.ofMinutes(1);
    public static final int MAX_AVAILABILITY_CHECKS_PER_IP = 10;
    public static final int MAX_FAILED_ATTEMPTS = 5;

    private EmailVerificationPolicy() {}
}
