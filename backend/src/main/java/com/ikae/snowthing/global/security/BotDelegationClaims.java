package com.ikae.snowthing.global.security;

import java.time.Instant;
import java.util.List;

public record BotDelegationClaims(
        String subject,
        String tokenId,
        String chatMessageId,
        Instant issuedAt,
        Instant expiresAt,
        List<String> scopes) {

    public BotDelegationClaims {
        scopes = List.copyOf(scopes);
    }
}
