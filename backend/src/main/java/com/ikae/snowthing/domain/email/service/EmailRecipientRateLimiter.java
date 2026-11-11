package com.ikae.snowthing.domain.email.service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 한 메일함으로 가는 인증 메일 수를 제한한다. 이메일별 제한(EmailVerificationWriter)은 입력한 주소와 용도마다 따로 세므로 {@code
 * a+1@gmail.com}, {@code a.b@gmail.com}처럼 같은 메일함으로 가는 별칭을 바꿔 가며 보내면 막지 못한다. 여기서는 별칭을 한 메일함으로 묶고 가입과
 * 비밀번호 재설정을 합쳐 센다.
 *
 * <p>IP 제한과 같은 프로세스 메모리 고정 창이다. 서버를 다시 띄우면 초기화되지만 메일 폭탄을 막는 목적에는 충분하다.
 */
@Component
public class EmailRecipientRateLimiter {

    static final Duration HOURLY_WINDOW = Duration.ofHours(1);
    static final Duration DAILY_WINDOW = Duration.ofDays(1);

    private final int hourlyLimit;
    private final int dailyLimit;
    private final Clock clock;
    private final Cache<String, AtomicInteger> counts =
            Caffeine.newBuilder()
                    .expireAfterWrite(DAILY_WINDOW.plusHours(1))
                    .maximumSize(50_000)
                    .build();

    public EmailRecipientRateLimiter(
            @Value("${snowthing.email.verification.recipient-hourly-limit:10}") int hourlyLimit,
            @Value("${snowthing.email.verification.recipient-daily-limit:20}") int dailyLimit,
            Clock clock) {
        this.hourlyLimit = hourlyLimit;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    /** 두 창이 모두 남아 있을 때만 하나씩 쓴다. 거절된 요청은 세지 않아 한도에 걸린 동안 계속 눌러도 창이 늘어나지 않는다. */
    public synchronized void check(String email) {
        String recipient = canonicalRecipient(email);
        long epochSecond = clock.instant().getEpochSecond();
        AtomicInteger hourly = counter("h", recipient, epochSecond, HOURLY_WINDOW);
        AtomicInteger daily = counter("d", recipient, epochSecond, DAILY_WINDOW);
        if (hourly.get() >= hourlyLimit || daily.get() >= dailyLimit) {
            throw new CustomException(ErrorCode.EMAIL_SEND_LIMIT);
        }
        hourly.incrementAndGet();
        daily.incrementAndGet();
    }

    private AtomicInteger counter(
            String scope, String recipient, long epochSecond, Duration window) {
        long index = epochSecond / window.toSeconds();
        return counts.asMap()
                .computeIfAbsent(
                        scope + ':' + index + ':' + recipient, ignored -> new AtomicInteger());
    }

    /**
     * 같은 메일함으로 가는 주소를 하나로 모은다. 소문자로 바꾸고 로컬 부분의 {@code +태그}를 지운다. Gmail은 로컬 부분의 점을 무시하고
     * googlemail.com도 같은 메일함이므로 함께 정리한다. 가입 주소 자체는 바꾸지 않고 횟수를 세는 키로만 쓴다.
     */
    static String canonicalRecipient(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = normalized.lastIndexOf('@');
        if (at <= 0) {
            return normalized;
        }
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at + 1);
        int plus = local.indexOf('+');
        if (plus > 0) {
            local = local.substring(0, plus);
        }
        if (domain.equals("googlemail.com")) {
            domain = "gmail.com";
        }
        if (domain.equals("gmail.com")) {
            local = local.replace(".", "");
        }
        return local + '@' + domain;
    }
}
