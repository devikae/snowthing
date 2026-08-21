package com.ikae.snowthing.domain.post.dto;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PostListResponse(
    String publicId,
    String categoryName,
    String categoryCode,
    String title,
    String writerNickname,
    String thumbnailImageUrl,
    int viewCount,
    int commentCount,
    int likeCount,
    int dislikeCount,
    PostStatus status,
    LocalDateTime createdAt
) {
    public static PostListResponse from(Post post) {
        String writerNickname = post.isAnonymous()
            ? "익명 (" + maskIp(post.getWriterIp()) + ")"
            : (post.getMember() != null ? post.getMember().getNickname() : "알 수 없음");

        String thumbnailUrl = post.getImages().isEmpty() ? null : post.getImages().get(0).getImageUrl();

        return PostListResponse.builder()
            .publicId(post.getPublicId())
            .categoryName(post.getCategory().getName())
            .categoryCode(post.getCategory().getCode())
            .title(post.getTitle())
            .writerNickname(writerNickname)
            .thumbnailImageUrl(thumbnailUrl)
            .viewCount(post.getViewCount())
            .commentCount(post.getCommentCount())
            .likeCount(post.getLikeCount())
            .dislikeCount(post.getDislikeCount())
            .status(post.getStatus())
            .createdAt(post.getCreatedAt())
            .build();
    }

    private static String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return "***.***.***.***";
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".***.***";
        }
        return ip;
    }
}
