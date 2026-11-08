package com.ikae.snowthing.domain.carpool.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.web.ClientIpResolver;

@Component
public class CarpoolExternalApiRateLimiter {

    private static final int PREVIEW_REQUESTS_PER_MINUTE = 12;
    private static final int PLACE_REQUESTS_PER_MINUTE = 30;
    private static final long RATE_LIMIT_WINDOW_SECONDS = 60L;
    private static final int MAX_TRACKED_WINDOWS = 10_000;
    private static final Duration ENTRY_RETENTION = Duration.ofMinutes(2);
    private static final String PREVIEW_SCOPE = "preview";
    private static final String PLACE_SCOPE = "place";

    // 비회원도 부를 수 있어 IP 제한만으로는 Kakao 일일 쿼터를 지키지 못한다. 서비스 전체 하루 상한을 따로 둔다.
    private final int previewDailyLimit;
    private final int placeDailyLimit;
    private final Cache<String, AtomicInteger> dailyCounts =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(2)).maximumSize(16).build();

    private final Cache<String, AtomicInteger> requestCounts =
            Caffeine.newBuilder()
                    .expireAfterWrite(ENTRY_RETENTION)
                    .maximumSize(MAX_TRACKED_WINDOWS)
                    .build();

    public CarpoolExternalApiRateLimiter(
            @Value("${snowthing.carpool.external-api.preview-daily-limit:5000}")
                    int previewDailyLimit,
            @Value("${snowthing.carpool.external-api.place-daily-limit:20000}")
                    int placeDailyLimit) {
        this.previewDailyLimit = previewDailyLimit;
        this.placeDailyLimit = placeDailyLimit;
    }

    public void checkPreview(String clientIp) {
        check(clientIp, PREVIEW_SCOPE, PREVIEW_REQUESTS_PER_MINUTE);
        checkDaily(PREVIEW_SCOPE, previewDailyLimit);
    }

    public void checkPlaceSearch(String clientIp) {
        check(clientIp, PLACE_SCOPE, PLACE_REQUESTS_PER_MINUTE);
        checkDaily(PLACE_SCOPE, placeDailyLimit);
    }

    private void check(String clientIp, String scope, int requestLimit) {
        long window = Instant.now().getEpochSecond() / RATE_LIMIT_WINDOW_SECONDS;
        // IPv6는 /64로 묶어 주소를 바꿔 가며 우회하지 못하게 한다.
        String normalizedClient = ClientIpResolver.normalizeForLimit(clientIp);
        String key = scope + ':' + normalizedClient + ':' + window;
        AtomicInteger counter =
                requestCounts.asMap().computeIfAbsent(key, ignored -> new AtomicInteger());
        if (counter.incrementAndGet() > requestLimit) {
            throw new CustomException(ErrorCode.CARPOOL_EXTERNAL_API_RATE_LIMIT);
        }
    }

    private void checkDaily(String scope, int dailyLimit) {
        String key = scope + ':' + LocalDate.now(ZoneId.of("Asia/Seoul"));
        AtomicInteger counter =
                dailyCounts.asMap().computeIfAbsent(key, ignored -> new AtomicInteger());
        if (counter.incrementAndGet() > dailyLimit) {
            throw new CustomException(ErrorCode.CARPOOL_EXTERNAL_API_RATE_LIMIT);
        }
    }
}
