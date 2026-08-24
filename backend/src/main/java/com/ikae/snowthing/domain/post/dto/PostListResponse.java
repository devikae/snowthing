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
    boolean hasImage,
    int viewCount,
    int commentCount,
    int likeCount,
    int dislikeCount,
    PostStatus status,
    boolean isDeleted,
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
            .title(post.isDeleted() ? "[삭제된 게시글입니다]" : post.getTitle())
            .writerNickname(writerNickname)
            .thumbnailImageUrl(thumbnailUrl)
            .hasImage(post.isHasImage())
            .viewCount(post.getViewCount())
            .commentCount(post.getCommentCount())
            .likeCount(post.getLikeCount())
            .dislikeCount(post.getDislikeCount())
            .status(post.getStatus())
            .isDeleted(post.isDeleted())
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
