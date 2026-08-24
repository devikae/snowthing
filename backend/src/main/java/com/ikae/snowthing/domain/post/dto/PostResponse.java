package com.ikae.snowthing.domain.post.dto;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostResponse(
    String publicId,
    String title,
    String writerName,
    PostStatus status,
    LocalDateTime createdAt
) {
    public static PostResponse from(Post post) {
        String writerName = post.isAnonymous()
            ? "익명 (" + maskIp(post.getWriterIp()) + ")"
            : (post.getMember() != null ? post.getMember().getNickname() : "알 수 없음");

        return PostResponse.builder()
            .publicId(post.getPublicId())
            .title(post.getTitle())
            .writerName(writerName)
            .status(post.getStatus())
            .createdAt(post.getCreatedAt())
            .build();
    }

    private static String maskIp(String ip) {
        if (ip == null || ip.isBlank() || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.***.***";
        }
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.***";
        }
        return ip;
    }
}
