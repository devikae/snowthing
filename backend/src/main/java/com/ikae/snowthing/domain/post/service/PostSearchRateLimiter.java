package com.ikae.snowthing.domain.post.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.web.ClientIpResolver;

/**
 * 게시판 검색어 검색을 IP별로 묶는다. 검색은 로그인 없이 쓰고 본문 LIKE로 글 전체를 훑으므로, 봇 몇 개가 연달아 보내면 DB가 버티지 못한다.
 *
 * <p>검색마다 DB에 횟수를 쓰면 막으려는 부하를 오히려 늘리므로 공유 제한기 대신 메모리에 둔다. 백엔드가 한 대라는 전제다.
 */
@Component
public class PostSearchRateLimiter {

    public static final int MIN_KEYWORD_LENGTH = 2;
    public static final int MAX_KEYWORD_LENGTH = 50;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final int maxPerWindow;
    private final Cache<String, RequestWindow> windows =
            Caffeine.newBuilder()
                    .expireAfterAccess(WINDOW.multipliedBy(2))
                    .maximumSize(10_000)
                    .build();

    public PostSearchRateLimiter(
            Clock clock, @Value("${snowthing.post-search.per-minute:30}") int maxPerWindow) {
        this.clock = clock;
        this.maxPerWindow = maxPerWindow;
    }

    public void check(String clientIp) {
        String key = ClientIpResolver.normalizeForLimit(clientIp);
        RequestWindow window = windows.asMap().computeIfAbsent(key, ignored -> new RequestWindow());
        if (!window.tryAcquire(clock.instant(), maxPerWindow)) {
            throw new CustomException(ErrorCode.POST_SEARCH_RATE_LIMIT);
        }
    }

    private static final class RequestWindow {
        private final Deque<Instant> requests = new ArrayDeque<>();

        private synchronized boolean tryAcquire(Instant now, int max) {
            Instant cutoff = now.minus(WINDOW);
            while (!requests.isEmpty() && !requests.getFirst().isAfter(cutoff)) {
                requests.removeFirst();
            }
            if (requests.size() >= max) return false;
            requests.addLast(now);
            return true;
        }
    }
}
