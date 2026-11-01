package com.ikae.snowthing.domain.member.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

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
    private final Cache<Key, Window> windows =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterAccess(Duration.ofHours(2))
                    .build();

    public ProfileActionRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String member, Action action) {
        Window window = windows.get(new Key(member, action), ignored -> new Window());
        if (!window.acquire(clock.instant(), action)) {
            throw new CustomException(ErrorCode.PROFILE_ACTION_LIMIT);
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
