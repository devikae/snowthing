package com.ikae.snowthing.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/** 글, 댓글 같은 작성 요청을 작성자별로 제한해 연타와 도배를 막는다. 비로그인 작성은 IP로 묶는다. */
@Component
public class ContentWriteRateLimiter {
    public enum Action {
        POST(Duration.ofSeconds(30), 20),
        COMMENT(Duration.ofSeconds(3), 60),
        RESORT_REPORT(Duration.ofSeconds(10), 10),
        CARPOOL(Duration.ofSeconds(30), 10),
        MARKET_LISTING(Duration.ofSeconds(30), 10);

        // 짧은 간격마다 1건, 1시간마다 hourlyLimit건까지 허용한다.
        final Duration interval;
        final int hourlyLimit;

        Action(Duration interval, int hourlyLimit) {
            this.interval = interval;
            this.hourlyLimit = hourlyLimit;
        }
    }

    private final SharedRequestLimiter sharedLimiter;
    private final boolean enabled;

    public ContentWriteRateLimiter(
            SharedRequestLimiter sharedLimiter,
            @Value("${snowthing.write-limit.enabled:true}") boolean enabled) {
        this.sharedLimiter = sharedLimiter;
        this.enabled = enabled;
    }

    public void check(CustomUserDetails userDetails, String clientIp, Action action) {
        if (!enabled) return;
        String actor =
                userDetails != null
                        ? "member:" + userDetails.getUsername()
                        : "ip:" + (clientIp == null || clientIp.isBlank() ? "unknown" : clientIp);
        String prefix =
                "snowthing:limits:write:"
                        + action.name().toLowerCase(Locale.ROOT)
                        + ":"
                        // budget_key는 VARCHAR(100)이라 해시 앞 32자(128비트)만 쓴다.
                        + sha256(actor).substring(0, 32);
        var decision =
                sharedLimiter.acquire(
                        List.of(
                                new SharedRequestLimiter.Limit(
                                        prefix + ":burst", 1, action.interval),
                                new SharedRequestLimiter.Limit(
                                        prefix + ":hour",
                                        action.hourlyLimit,
                                        Duration.ofHours(1))));
        if (!decision.allowed())
            throw new CustomException(
                    decision.available()
                            ? ErrorCode.CONTENT_WRITE_LIMIT
                            : ErrorCode.CONTENT_WRITE_LIMIT_UNAVAILABLE);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }
}
