package com.ikae.snowthing.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class PostSearchRateLimiterTest {

    private static final int LIMIT = 30;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T00:00:00Z"));
    private final PostSearchRateLimiter limiter = new PostSearchRateLimiter(clock, LIMIT);

    @Test
    void rejectsSearchOverLimitFromSameIpWithinOneMinute() {
        for (int count = 0; count < LIMIT; count++) {
            limiter.check("203.0.113.10");
        }

        assertThatThrownBy(() -> limiter.check("203.0.113.10"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.POST_SEARCH_RATE_LIMIT));
    }

    @Test
    void otherIpIsNotAffected() {
        for (int count = 0; count < LIMIT; count++) {
            limiter.check("203.0.113.10");
        }

        limiter.check("203.0.113.11");
    }

    @Test
    void allowsAgainAfterWindowPasses() {
        for (int count = 0; count < LIMIT; count++) {
            limiter.check("203.0.113.10");
        }

        clock.advanceSeconds(60);

        limiter.check("203.0.113.10");
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
