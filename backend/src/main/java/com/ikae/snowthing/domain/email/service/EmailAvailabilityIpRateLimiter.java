package com.ikae.snowthing.domain.email.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.email.EmailVerificationPolicy;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.web.ClientIpResolver;

@Component
public class EmailAvailabilityIpRateLimiter {

    private final Clock clock;
    private final Cache<String, RequestWindow> windows =
            Caffeine.newBuilder()
                    .expireAfterAccess(
                            EmailVerificationPolicy.AVAILABILITY_CHECK_WINDOW.multipliedBy(2))
                    .maximumSize(10_000)
                    .build();

    public EmailAvailabilityIpRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public void check(String clientIp) {
        String normalized = ClientIpResolver.normalizeForLimit(clientIp);
        RequestWindow window =
                windows.asMap().computeIfAbsent(normalized, ignored -> new RequestWindow());

        if (!window.tryAcquire(clock.instant())) {
            throw new CustomException(ErrorCode.EMAIL_AVAILABILITY_LIMIT);
        }
    }

    private static final class RequestWindow {

        private final Deque<Instant> requests = new ArrayDeque<>();

        private synchronized boolean tryAcquire(Instant now) {
            Instant cutoff = now.minus(EmailVerificationPolicy.AVAILABILITY_CHECK_WINDOW);
            while (!requests.isEmpty() && !requests.getFirst().isAfter(cutoff)) {
                requests.removeFirst();
            }

            if (requests.size() >= EmailVerificationPolicy.MAX_AVAILABILITY_CHECKS_PER_IP) {
                return false;
            }

            requests.addLast(now);
            return true;
        }
    }
}
