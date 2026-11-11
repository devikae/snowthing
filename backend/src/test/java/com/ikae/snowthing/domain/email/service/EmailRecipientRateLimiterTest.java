package com.ikae.snowthing.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

class EmailRecipientRateLimiterTest {

    private static final Instant START = Instant.parse("2026-11-11T00:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final EmailRecipientRateLimiter limiter = new EmailRecipientRateLimiter(10, 20, clock);

    @Test
    @DisplayName("별칭과 대소문자, Gmail 점 표기를 같은 메일함으로 묶는다")
    void canonicalizesAliases() {
        assertThat(EmailRecipientRateLimiter.canonicalRecipient(" A.B+promo@GoogleMail.com "))
                .isEqualTo("ab@gmail.com");
        assertThat(EmailRecipientRateLimiter.canonicalRecipient("a.b+1@snowthing.org"))
                .isEqualTo("a.b@snowthing.org");
        assertThat(EmailRecipientRateLimiter.canonicalRecipient("User@Example.com"))
                .isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("별칭을 바꿔 가며 보내도 한 메일함은 한 시간에 10통까지만 받는다")
    void aliasesShareHourlyLimit() {
        for (int i = 0; i < 10; i++) {
            limiter.check("victim+" + i + "@gmail.com");
        }

        assertThatThrownBy(() -> limiter.check("v.i.c.t.i.m@googlemail.com"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_LIMIT);
        assertThatCode(() -> limiter.check("other@gmail.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("두세 번 다시 보내는 정도는 막지 않고, 한 시간이 지나면 다시 보낼 수 있다")
    void normalResendIsAllowedAndHourlyWindowResets() {
        for (int i = 0; i < 3; i++) {
            assertThatCode(() -> limiter.check("member@snowthing.org")).doesNotThrowAnyException();
        }
        for (int i = 0; i < 7; i++) {
            limiter.check("member@snowthing.org");
        }
        assertThatThrownBy(() -> limiter.check("member@snowthing.org"))
                .isInstanceOf(CustomException.class);

        clock.advance(Duration.ofHours(1));

        assertThatCode(() -> limiter.check("member@snowthing.org")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("시간마다 나눠 보내도 하루 20통을 넘기지 못하고, 다음 날이면 다시 보낼 수 있다")
    void dailyLimitCapsSpreadRequests() {
        for (int i = 0; i < 10; i++) {
            limiter.check("member@snowthing.org");
        }
        clock.advance(Duration.ofHours(1));
        for (int i = 0; i < 10; i++) {
            limiter.check("member@snowthing.org");
        }
        clock.advance(Duration.ofHours(1));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> limiter.check("member@snowthing.org"))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.EMAIL_SEND_LIMIT);
        }

        clock.advance(Duration.ofDays(1));
        assertThatCode(() -> limiter.check("member@snowthing.org")).doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
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
