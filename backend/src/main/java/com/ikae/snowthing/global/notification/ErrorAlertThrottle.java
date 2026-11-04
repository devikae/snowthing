package com.ikae.snowthing.global.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 같은 키의 장애 알림을 5분에 한 번으로 줄인다. 장애 중 같은 에러가 수백 번 나도 채널이 도배되거나 웹훅 레이트 리밋에 걸리지 않게 한다.
 *
 * <p>상태는 메모리에만 둔다. 재시작하면 초기화되고, 인스턴스가 여럿이면 인스턴스마다 따로 센다.
 */
@Component
public class ErrorAlertThrottle {

    static final Duration WINDOW = Duration.ofMinutes(5);
    static final int MAX_KEYS = 500;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public ErrorAlertThrottle() {
        this(Clock.systemUTC());
    }

    ErrorAlertThrottle(Clock clock) {
        this.clock = clock;
    }

    /** 보낼 차례면 지난 알림 이후 억제된 횟수를, 억제할 차례면 빈 값을 돌려준다. */
    public OptionalLong tryAcquire(String key) {
        Instant now = clock.instant();
        long[] suppressedBefore = {-1};
        windows.compute(
                key,
                (k, window) -> {
                    if (window == null || window.expired(now)) {
                        suppressedBefore[0] = window == null ? 0 : window.suppressed();
                        return new Window(now, 0);
                    }
                    return new Window(window.sentAt(), window.suppressed() + 1);
                });
        if (windows.size() > MAX_KEYS) {
            windows.values().removeIf(window -> window.expired(now));
        }
        return suppressedBefore[0] < 0
                ? OptionalLong.empty()
                : OptionalLong.of(suppressedBefore[0]);
    }

    private record Window(Instant sentAt, long suppressed) {

        boolean expired(Instant now) {
            return !now.isBefore(sentAt.plus(WINDOW));
        }
    }
}
