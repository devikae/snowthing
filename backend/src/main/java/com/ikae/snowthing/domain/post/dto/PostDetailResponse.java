package com.ikae.snowthing.domain.post.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostImage;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

public record PostDetailResponse(
        String publicId,
        String categoryName,
        String categoryCode,
        String title,
        String content,
        PostStatus status,
        boolean isAnonymous,
        int viewCount,
        int commentCount,
        int likeCount,
        int dislikeCount,
        WriterInfo writer,
        List<String> images,
        LocalDateTime createdAt) {
    public record WriterInfo(String publicId, String nickname, String profileImageUrl) {
        public static WriterInfo anonymous(String maskedIp) {
            return new WriterInfo(null, maskedIp, null);
        }

        public static WriterInfo member(String publicId, String nickname, String profileImageUrl) {
            return new WriterInfo(publicId, nickname, profileImageUrl);
        }
    }

    public static PostDetailResponse from(Post post) {
        WriterInfo writerInfo =
                post.isAnonymous()
                        ? WriterInfo.anonymous(
                                WriterDisplayFormatter.formatAnonymous(post.getWriterIp()))
                        : (post.getMember() != null
                                ? WriterInfo.member(
                                        post.getMember().getPublicId(),
                                        post.getMember().getNickname(),
                                        post.getMember().getProfileImageUrl())
                                : WriterInfo.anonymous("알 수 없음"));

        List<String> imageUrls = post.getImages().stream().map(PostImage::getImageUrl).toList();

        return new PostDetailResponse(
                post.getPublicId(),
                post.getCategory().getName(),
                post.getCategory().getCode(),
                post.getTitle(),
                post.getContent(),
                post.getStatus(),
                post.isAnonymous(),
                post.getViewCount(),
                post.getCommentCount(),
                post.getLikeCount(),
                post.getDislikeCount(),
                writerInfo,
                List.copyOf(imageUrls),
                post.getCreatedAt());
    }
}
