package com.ikae.snowthing.domain.member.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ikae.snowthing.domain.image.service.ImageBudgetMonitor;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;
import com.ikae.snowthing.global.security.SharedRequestLimiter;

@Component
public class ProfileActionRateLimiter {
    public enum Action {
        PASSWORD(10, Duration.ofMinutes(15)),
        NICKNAME(30, Duration.ofMinutes(1)),
        PHOTO(5, Duration.ofDays(1)),
        POST_IMAGE(30, Duration.ofHours(1)),
        IMAGE_LEASE(60, Duration.ofHours(1)),
        MEMBER_SEARCH(60, Duration.ofMinutes(1));

        final int limit;
        final Duration window;

        Action(int limit, Duration window) {
            this.limit = limit;
            this.window = window;
        }
    }

    private record Key(String member, Action action) {}

    private final Clock clock;
    private final SharedRequestLimiter sharedLimiter;
    private final ImageBudgetMonitor imageBudgetMonitor;
    private final Cache<Key, Window> windows =
            Caffeine.newBuilder()
                    .maximumSize(10_000)
                    .expireAfterAccess(Duration.ofHours(2))
                    .build();

    public ProfileActionRateLimiter(
            Clock clock,
            SharedRequestLimiter sharedLimiter,
            ImageBudgetMonitor imageBudgetMonitor) {
        this.clock = clock;
        this.sharedLimiter = sharedLimiter;
        this.imageBudgetMonitor = imageBudgetMonitor;
    }

    public void check(String member, Action action) {
        check(member, action, null, null, false);
    }

    public void checkImage(
            String member, Action action, LocalDateTime registeredAt, String clientIp) {
        if (action != Action.PHOTO && action != Action.POST_IMAGE)
            throw new CustomException(ErrorCode.INVALID_INPUT);
        check(member, action, registeredAt, clientIp, true);
    }

    private void check(
            String member,
            Action action,
            LocalDateTime registeredAt,
            String clientIp,
            boolean imageRequest) {
        if (action != Action.NICKNAME && action != Action.MEMBER_SEARCH) {
            String memberHash = memberKey(member);
            var budgets =
                    new java.util.ArrayList<>(
                            List.of(
                                    new SharedRequestLimiter.Limit(
                                            "snowthing:limits:"
                                                    + action.name()
                                                            .toLowerCase(java.util.Locale.ROOT)
                                                    + ":"
                                                    + memberHash,
                                            action.limit,
                                            action.window)));
            if (action == Action.PHOTO || action == Action.POST_IMAGE) {
                budgets.add(
                        new SharedRequestLimiter.Limit(
                                "snowthing:limits:images:global:hour", 300, Duration.ofHours(1)));
                budgets.add(
                        new SharedRequestLimiter.Limit(
                                "snowthing:limits:images:global:day", 1000, Duration.ofDays(1)));
            }
            if (action == Action.PHOTO) {
                budgets.add(
                        new SharedRequestLimiter.Limit(
                                "snowthing:limits:photos:global:day", 30, Duration.ofDays(1)));
            } else if (action == Action.POST_IMAGE) {
                budgets.add(
                        new SharedRequestLimiter.Limit(
                                "snowthing:limits:post-images:global:hour",
                                270,
                                Duration.ofHours(1)));
                budgets.add(
                        new SharedRequestLimiter.Limit(
                                "snowthing:limits:post-images:global:day",
                                970,
                                Duration.ofDays(1)));
            }
            if (imageRequest) {
                String scope = action == Action.PHOTO ? "photo" : "post-image";
                // Principal timestamps are immutable server data. A missing timestamp is treated
                // conservatively.
                LocalDateTime cutoff =
                        LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())
                                .minusDays(1);
                if (registeredAt == null || registeredAt.isAfter(cutoff))
                    budgets.add(
                            new SharedRequestLimiter.Limit(
                                    "snowthing:limits:new-" + scope + ":" + memberHash,
                                    action == Action.PHOTO ? 1 : 5,
                                    action.window));
                String normalizedIp = normalizeIp(clientIp);
                String ipHash = memberKey(normalizedIp);
                budgets.add(
                        new SharedRequestLimiter.Limit(
                                "snowthing:limits:ip-" + scope + ":" + ipHash,
                                action == Action.PHOTO ? 10 : 60,
                                action.window));
                if (action == Action.POST_IMAGE)
                    budgets.add(
                            new SharedRequestLimiter.Limit(
                                    "snowthing:limits:ip-post-day:" + ipHash,
                                    200,
                                    Duration.ofDays(1)));
            }
            var decision = sharedLimiter.acquire(budgets);
            if (decision.available()
                    && decision.deniedKey() != null
                    && decision.deniedKey().contains(":global:")) {
                imageBudgetMonitor.rejected(action);
                throw new CustomException(ErrorCode.IMAGE_GLOBAL_LIMIT);
            }
            if (!decision.allowed())
                throw new CustomException(
                        decision.available()
                                ? ErrorCode.PROFILE_ACTION_LIMIT
                                : ErrorCode.PROFILE_LIMIT_UNAVAILABLE);
            return;
        }
        Window window = windows.get(new Key(member, action), ignored -> new Window());
        if (!window.acquire(clock.instant(), action)) {
            throw new CustomException(ErrorCode.PROFILE_ACTION_LIMIT);
        }
    }

    private String normalizeIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return "unknown";
        try {
            // Never resolve hostnames supplied by a request. Only numeric IPv4/IPv6 literals are
            // accepted.
            if (!clientIp.matches("[0-9a-fA-F:.]+")) return "unknown";
            byte[] address;
            if (!clientIp.contains(":")) {
                if (!clientIp.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) return "unknown";
                String[] octets = clientIp.split("\\.");
                address = new byte[4];
                for (int i = 0; i < octets.length; i++) {
                    int octet = Integer.parseInt(octets[i]);
                    if (octet > 255) return "unknown";
                    address[i] = (byte) octet;
                }
            } else {
                address = java.net.InetAddress.getByName(clientIp).getAddress();
            }
            // Group IPv6 privacy addresses by /64 so rotating interface identifiers cannot reset a
            // bucket.
            if (address.length == 16) java.util.Arrays.fill(address, 8, 16, (byte) 0);
            return java.util.HexFormat.of().formatHex(address);
        } catch (java.net.UnknownHostException invalidIp) {
            return "unknown";
        }
    }

    private String memberKey(String member) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            member.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private static final class Window {
        private final Deque<Instant> requests = new ArrayDeque<>();

        synchronized boolean acquire(Instant now, Action action) {
            Instant cutoff = now.minus(action.window);
            while (!requests.isEmpty() && !requests.getFirst().isAfter(cutoff))
                requests.removeFirst();
            if (requests.size() >= action.limit) return false;
            requests.addLast(now);
            return true;
        }
    }
}
