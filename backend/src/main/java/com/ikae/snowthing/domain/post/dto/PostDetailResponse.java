package com.ikae.snowthing.domain.post.dto;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostImage;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record PostDetailResponse(
    String publicId,
    String categoryName,
    String categoryCode,
    String title,
    String content,
    PostStatus status,
    int viewCount,
    int commentCount,
    int likeCount,
    int dislikeCount,
    WriterInfo writer,
    List<String> images,
    LocalDateTime createdAt
) {
    @Builder
    public record WriterInfo(
        String publicId,
        String nickname,
        String profileImageUrl
    ) {}

    public static PostDetailResponse from(Post post) {
        WriterInfo writerInfo;
        if (post.isAnonymous()) {
            writerInfo = WriterInfo.builder()
                .publicId(null)
                .nickname("익명 (" + maskIp(post.getWriterIp()) + ")")
                .profileImageUrl(null)
                .build();
        } else if (post.getMember() != null) {
            writerInfo = WriterInfo.builder()
                .publicId(post.getMember().getPublicId())
                .nickname(post.getMember().getNickname())
                .profileImageUrl(post.getMember().getProfileImageUrl())
                .build();
        } else {
            writerInfo = WriterInfo.builder()
                .publicId(null)
                .nickname("알 수 없음")
                .profileImageUrl(null)
                .build();
        }

        List<String> imageUrls = post.getImages().stream()
            .map(PostImage::getImageUrl)
            .toList();

        return PostDetailResponse.builder()
            .publicId(post.getPublicId())
            .categoryName(post.getCategory().getName())
            .categoryCode(post.getCategory().getCode())
            .title(post.getTitle())
            .content(post.getContent())
            .status(post.getStatus())
            .viewCount(post.getViewCount())
            .commentCount(post.getCommentCount())
            .likeCount(post.getLikeCount())
            .dislikeCount(post.getDislikeCount())
            .writer(writerInfo)
            .images(imageUrls)
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
