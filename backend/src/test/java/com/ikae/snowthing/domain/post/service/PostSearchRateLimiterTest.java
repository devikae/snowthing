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

    @Test
    void keywordSearchRejectsTooShortOrTooLongKeyword() {
        assertThatThrownBy(() -> limiter.checkKeywordSearch(" 보 ", "203.0.113.10"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.POST_SEARCH_KEYWORD_INVALID));
        assertThatThrownBy(() -> limiter.checkKeywordSearch("가".repeat(51), "203.0.113.10"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.POST_SEARCH_KEYWORD_INVALID));
    }

    @Test
    void blankKeywordIsNotCounted() {
        for (int count = 0; count < LIMIT + 5; count++) {
            limiter.checkKeywordSearch("  ", "203.0.113.10");
            limiter.checkKeywordSearch(null, "203.0.113.10");
        }

        limiter.checkKeywordSearch("보드", "203.0.113.10");
    }

    @Test
    void keywordSearchesShareOneBudgetPerIp() {
        // 게시판, 카풀, 중고장터 검색이 같은 한도를 나눠 쓰므로 어느 검색으로 채워도 다음 검색은 거절된다.
        for (int count = 0; count < LIMIT; count++) {
            limiter.checkKeywordSearch("보드", "203.0.113.10");
        }

        assertThatThrownBy(() -> limiter.checkKeywordSearch("강남", "203.0.113.10"))
                .isInstanceOfSatisfying(
                        CustomException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(ErrorCode.POST_SEARCH_RATE_LIMIT));
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
