package com.ikae.snowthing.domain.post.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

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
        LocalDateTime createdAt) {
    public static PostListResponse from(Post post) {
        String writerNickname =
                WriterDisplayFormatter.format(
                        post.isAnonymous(), post.getMember(), post.getWriterIp());

        String thumbnailUrl =
                post.getImages().isEmpty() ? null : post.getImages().get(0).getImageUrl();

        return new PostListResponse(
                post.getPublicId(),
                post.getCategory().getName(),
                post.getCategory().getCode(),
                post.isDeleted() ? "[삭제된 게시글입니다]" : post.getTitle(),
                writerNickname,
                thumbnailUrl,
                post.isHasImage(),
                post.getViewCount(),
                post.getCommentCount(),
                post.getLikeCount(),
                post.getDislikeCount(),
                post.getStatus(),
                post.isDeleted(),
                post.getCreatedAt());
    }
}
