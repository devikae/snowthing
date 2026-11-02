package com.ikae.snowthing.domain.member.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.SharedRequestLimiter;

@Component
public class ProfileActionRateLimiter {
    public enum Action {
        PASSWORD(10, Duration.ofMinutes(15)),
        NICKNAME(30, Duration.ofMinutes(1)),
        PHOTO(20, Duration.ofHours(1));

        final int limit;
        final Duration window;

        Action(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
        }
    }

    private record Key(String member, Action action) {}

    private final Clock clock;
    private final SharedRequestLimiter sharedLimiter;
    private final Cache<Key, Window> windows =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterAccess(Duration.ofHours(2))
                    .build();

    public ProfileActionRateLimiter(Clock clock, SharedRequestLimiter sharedLimiter) {
        this.clock = clock;
        this.sharedLimiter = sharedLimiter;
    }

    public void check(String member, Action action) {
        if (action == Action.PASSWORD) {
            String memberHash = memberKey(member);
            var decision =
                    sharedLimiter.acquire(
                            List.of(
                                    new SharedRequestLimiter.Limit(
                                            "snowthing:limits:password:" + memberHash,
                                            action.limit,
                                            action.window)));
            if (!decision.allowed())
                throw new CustomException(
                        decision.available()
                                ? ErrorCode.PROFILE_ACTION_LIMIT
                                : ErrorCode.PROFILE_LIMIT_UNAVAILABLE);
            return;
        }
        Window window = windows.get(new Key(member, action), ignored -> new Window());
        if (!window.acquire(clock.instant(), action)) {
            throw new CustomException(ErrorCode.PROFILE_ACTION_LIMIT);
        }
    }

    private String memberKey(String member) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            member.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private static final class Window {
        private final Deque<Instant> requests = new ArrayDeque<>();

        synchronized boolean acquire(Instant now, Action action) {
            Instant cutoff = now.minus(action.window);
            while (!requests.isEmpty() && !requests.getFirst().isAfter(cutoff))
                requests.removeFirst();
            if (requests.size() >= action.limit) return false;
            requests.addLast(now);
            return true;
        }
    }
}
