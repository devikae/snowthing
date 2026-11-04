package com.ikae.snowthing.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorAlertThrottleTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T05:00:00Z"));
    private final ErrorAlertThrottle throttle = new ErrorAlertThrottle(clock);

    @Test
    @DisplayName("같은 키는 5분에 한 번만 보내고 다음 알림에 억제 횟수를 넘긴다")
    void suppressesWithinWindowAndCountsSuppressed() {
        assertThat(throttle.tryAcquire("A")).isEqualTo(OptionalLong.of(0));
        assertThat(throttle.tryAcquire("A")).isEmpty();
        clock.advance(Duration.ofMinutes(4));
        assertThat(throttle.tryAcquire("A")).isEmpty();

        clock.advance(Duration.ofMinutes(1));

        assertThat(throttle.tryAcquire("A")).isEqualTo(OptionalLong.of(2));
        assertThat(throttle.tryAcquire("A")).isEmpty();
    }

    @Test
    @DisplayName("키가 다르면 따로 센다")
    void separatesKeys() {
        assertThat(throttle.tryAcquire("A")).isPresent();
        assertThat(throttle.tryAcquire("B")).isPresent();
    }

    @Test
    @DisplayName("키가 너무 많아지면 만료된 키를 정리한다")
    void evictsExpiredKeys() {
        for (int i = 0; i <= ErrorAlertThrottle.MAX_KEYS; i++) {
            throttle.tryAcquire("old-" + i);
        }
        clock.advance(ErrorAlertThrottle.WINDOW);

        throttle.tryAcquire("new");

        assertThat(throttle.tryAcquire("old-0")).isEqualTo(OptionalLong.of(0));
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
