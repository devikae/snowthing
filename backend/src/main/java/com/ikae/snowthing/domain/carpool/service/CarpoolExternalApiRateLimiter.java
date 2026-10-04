package com.ikae.snowthing.domain.carpool.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@Component
public class CarpoolExternalApiRateLimiter {

    private static final int PREVIEW_REQUESTS_PER_MINUTE = 12;
    private static final int PLACE_REQUESTS_PER_MINUTE = 30;
    private static final long RATE_LIMIT_WINDOW_SECONDS = 60L;
    private static final int MAX_TRACKED_WINDOWS = 10_000;
    private static final Duration ENTRY_RETENTION = Duration.ofMinutes(2);
    private static final String PREVIEW_SCOPE = "preview";
    private static final String PLACE_SCOPE = "place";
    private static final String UNKNOWN_CLIENT = "unknown";

    private final Cache<String, AtomicInteger> requestCounts =
            Caffeine.newBuilder()
                    .expireAfterWrite(ENTRY_RETENTION)
                    .maximumSize(MAX_TRACKED_WINDOWS)
                    .build();

    public void checkPreview(String clientIp) {
        check(clientIp, PREVIEW_SCOPE, PREVIEW_REQUESTS_PER_MINUTE);
    }

    public void checkPlaceSearch(String clientIp) {
        check(clientIp, PLACE_SCOPE, PLACE_REQUESTS_PER_MINUTE);
    }

    private void check(String clientIp, String scope, int requestLimit) {
        long window = Instant.now().getEpochSecond() / RATE_LIMIT_WINDOW_SECONDS;
        String normalizedClient =
                clientIp == null || clientIp.isBlank() ? UNKNOWN_CLIENT : clientIp;
        String key = scope + ':' + normalizedClient + ':' + window;
        AtomicInteger counter =
                requestCounts.asMap().computeIfAbsent(key, ignored -> new AtomicInteger());
        if (counter.incrementAndGet() > requestLimit) {
            throw new CustomException(ErrorCode.CARPOOL_EXTERNAL_API_RATE_LIMIT);
        }
    }
}
