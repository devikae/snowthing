package com.ikae.snowthing.domain.email.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.email.EmailVerificationPolicy;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class EmailVerificationIpRateLimiterTest {

    @Test
    void twentyFirstRequestFromSameIpIsRejected() {
        EmailVerificationIpRateLimiter limiter = new EmailVerificationIpRateLimiter();
        for (int count = 0; count < EmailVerificationPolicy.MAX_SENDS_PER_IP; count++) {
            limiter.check("203.0.113.10");
        }

        assertThatThrownBy(() -> limiter.check("203.0.113.10"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_LIMIT);
        assertThatCode(() -> limiter.check("203.0.113.11")).doesNotThrowAnyException();
    }

    @Test
    void ipv6AddressesInSameSlash64ShareOneLimit() {
        EmailVerificationIpRateLimiter limiter = new EmailVerificationIpRateLimiter();
        for (int count = 0; count < EmailVerificationPolicy.MAX_SENDS_PER_IP; count++) {
            limiter.check("2001:db8:1:2::" + Integer.toHexString(count + 1));
        }

        assertThatThrownBy(() -> limiter.check("2001:db8:1:2:ffff:ffff:ffff:ffff"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_LIMIT);
        assertThatCode(() -> limiter.check("2001:db8:1:3::1")).doesNotThrowAnyException();
    }
}
