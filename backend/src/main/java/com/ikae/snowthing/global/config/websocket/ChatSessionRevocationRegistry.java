package com.ikae.snowthing.global.config.websocket;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Component
public class ChatSessionRevocationRegistry {

    private static final int MAX_REVOKED_SESSION_ENTRIES = 10_000;
    private static final Duration REVOCATION_RETENTION = Duration.ofDays(31);

    private final Cache<String, Boolean> revokedSessions =
            Caffeine.newBuilder()
                    .maximumSize(MAX_REVOKED_SESSION_ENTRIES)
                    .expireAfterWrite(REVOCATION_RETENTION)
                    .build();

    public void revoke(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            revokedSessions.put(sessionId, Boolean.TRUE);
        }
    }

    public boolean isRevoked(String sessionId) {
        return sessionId != null && revokedSessions.getIfPresent(sessionId) != null;
    }
}
