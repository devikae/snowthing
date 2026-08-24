package com.ikae.snowthing.global.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.springframework.util.StringUtils;

/**
 * 무상태 복합 커서 토큰 (Opaque Cursor Token) Base64 유틸리티.
 * 추후 Elasticsearch search_after 확장 대비 추상화.
 */
public class CursorUtils {

    public record CursorValue(
            Long likeCount,
            LocalDateTime createdAt,
            Long id
    ) {}

    /**
     * 최신순 (id) 또는 인기순 (likeCount + id) 커서 토큰 인코딩
     */
    public static String encode(Long likeCount, LocalDateTime createdAt, Long id) {
        if (id == null) return null;
        String raw = String.format("%d_%s_%d", 
                likeCount != null ? likeCount : 0L,
                createdAt != null ? createdAt.toString() : "",
                id
        );
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64 커서 토큰 디코딩
     */
    public static CursorValue decode(String cursorToken) {
        if (!StringUtils.hasText(cursorToken)) {
            return null;
        }
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(cursorToken);
            String decodedStr = new String(decodedBytes, StandardCharsets.UTF_8);
            String[] parts = decodedStr.split("_");
            if (parts.length < 3) {
                return null;
            }
            Long likeCount = Long.parseLong(parts[0]);
            LocalDateTime createdAt = StringUtils.hasText(parts[1]) ? LocalDateTime.parse(parts[1]) : null;
            Long id = Long.parseLong(parts[2]);
            return new CursorValue(likeCount, createdAt, id);
        } catch (Exception e) {
            return null;
        }
    }
}
