package com.ikae.snowthing.domain.email.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.email.EmailVerificationPolicy;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

@Component
public class EmailVerificationIpRateLimiter {

    private static final long WINDOW_SECONDS = EmailVerificationPolicy.SEND_WINDOW.toSeconds();
    private static final String UNKNOWN_CLIENT = "unknown";

    private final Cache<String, AtomicInteger> counts =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(2)).maximumSize(10_000).build();

    public void check(String clientIp) {
        String normalized = clientIp == null || clientIp.isBlank() ? UNKNOWN_CLIENT : clientIp;
        long window = Instant.now().getEpochSecond() / WINDOW_SECONDS;
        AtomicInteger count =
                counts.asMap()
                        .computeIfAbsent(normalized + ':' + window, ignored -> new AtomicInteger());
        if (count.incrementAndGet() > EmailVerificationPolicy.MAX_SENDS_PER_IP) {
            throw new CustomException(ErrorCode.EMAIL_SEND_LIMIT);
        }
    }
}
