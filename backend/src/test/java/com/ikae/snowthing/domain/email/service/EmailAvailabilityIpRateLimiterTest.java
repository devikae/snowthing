package com.ikae.snowthing.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.email.EmailVerificationPolicy;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class EmailAvailabilityIpRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-06T00:00:00Z"));
    private final EmailAvailabilityIpRateLimiter limiter =
            new EmailAvailabilityIpRateLimiter(clock);

    @Test
    void rejectsEleventhRequestFromSameIpWithinSixtySeconds() {
        for (int count = 0;
                count < EmailVerificationPolicy.MAX_AVAILABILITY_CHECKS_PER_IP;
                count++) {
            limiter.check("203.0.113.10");
        }

        assertThatThrownBy(() -> limiter.check("203.0.113.10"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.EMAIL_AVAILABILITY_LIMIT));
    }

    @Test
    void allowsRequestWhenOldestRequestLeavesSlidingWindow() {
        for (int count = 0;
                count < EmailVerificationPolicy.MAX_AVAILABILITY_CHECKS_PER_IP;
                count++) {
            limiter.check("203.0.113.10");
        }

        clock.advanceSeconds(60);

        limiter.check("203.0.113.10");
    }

    @Test
    void countsDifferentIpsSeparately() {
        for (int count = 0;
                count < EmailVerificationPolicy.MAX_AVAILABILITY_CHECKS_PER_IP;
                count++) {
            limiter.check("203.0.113.10");
        }

        limiter.check("203.0.113.11");
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
