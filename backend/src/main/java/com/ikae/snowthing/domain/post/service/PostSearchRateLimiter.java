package com.ikae.snowthing.domain.post.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.web.ClientIpResolver;

/**
 * 검색어 검색을 IP별로 묶는다. 게시판, 카풀 검색은 로그인 없이 쓰고 중고장터 검색도 회원이면 누구나 쓰며, 모두 LIKE로 글 전체를 훑으므로, 봇 몇 개가 연달아 보내면
 * DB가 버티지 못한다. 세 검색은 같은 IP 한도 하나를 나눠 쓴다.
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

    /**
     * 검색어가 있으면 길이를 확인하고 IP 한도를 센다. 한 글자 검색은 거의 모든 글에 걸려 의미가 없으므로 막는다. 검색어 없는 목록 조회는 인덱스를 타므로 세지
     * 않는다.
     */
    public void checkKeywordSearch(String keyword, String clientIp) {
        if (!StringUtils.hasText(keyword)) return;
        String stripped = keyword.strip();
        int length = stripped.codePointCount(0, stripped.length());
        if (length < MIN_KEYWORD_LENGTH || length > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.POST_SEARCH_KEYWORD_INVALID);
        }
        check(clientIp);
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
