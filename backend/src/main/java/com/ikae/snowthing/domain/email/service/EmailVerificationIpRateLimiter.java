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
import com.ikae.snowthing.global.web.ClientIpResolver;

@Component
public class EmailVerificationIpRateLimiter {

    private static final long WINDOW_SECONDS = EmailVerificationPolicy.SEND_WINDOW.toSeconds();

    private final Cache<String, AtomicInteger> counts =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(2)).maximumSize(10_000).build();

    public void check(String clientIp) {
        // IPv6는 /64로 묶는다. 한 회선이 인터페이스 주소를 바꿔 가며 새 횟수를 얻지 못하게 한다.
        String normalized = ClientIpResolver.normalizeForLimit(clientIp);
        long window = Instant.now().getEpochSecond() / WINDOW_SECONDS;
        AtomicInteger count =
                counts.asMap()
                        .computeIfAbsent(normalized + ':' + window, ignored -> new AtomicInteger());
        if (count.incrementAndGet() > EmailVerificationPolicy.MAX_SENDS_PER_IP) {
            throw new CustomException(ErrorCode.EMAIL_SEND_LIMIT);
        }
    }
}
