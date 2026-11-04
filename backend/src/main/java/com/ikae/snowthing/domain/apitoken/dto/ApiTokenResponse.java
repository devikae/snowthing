package com.ikae.snowthing.domain.apitoken.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ikae.snowthing.domain.apitoken.entity.ApiToken;

/** 토큰 목록에 보여 주는 정보. 평문과 해시는 담지 않는다. */
public record ApiTokenResponse(
        String tokenId,
        String name,
        String tokenPrefix,
        List<String> scopes,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime revokedAt,
        LocalDateTime lastUsedAt,
        boolean active) {

    public static ApiTokenResponse from(ApiToken token, LocalDateTime now) {
        return new ApiTokenResponse(
                token.getPublicId(),
                token.getName(),
                token.getTokenPrefix(),
                token.scopeSet().stream().map(Enum::name).toList(),
                token.getCreatedAt(),
                token.getExpiresAt(),
                token.getRevokedAt(),
                token.getLastUsedAt(),
                token.isUsableAt(now));
    }
}
