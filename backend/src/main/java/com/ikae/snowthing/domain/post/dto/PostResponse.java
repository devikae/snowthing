package com.ikae.snowthing.domain.post.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.post.entity.Post;
import com.ikae.snowthing.domain.post.entity.PostStatus;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

public record PostResponse(
        String publicId,
        String categoryName,
        String categoryCode,
        String title,
        String writerName,
        PostStatus status,
        LocalDateTime createdAt) {
    public static PostResponse from(Post post) {
        String writerName =
                WriterDisplayFormatter.format(
                        post.isAnonymous(), post.getMember(), post.getWriterIp());

        return new PostResponse(
                post.getPublicId(),
                post.getCategory().getName(),
                post.getCategory().getCode(),
                post.getTitle(),
                writerName,
                post.getStatus(),
                post.getCreatedAt());
    }
}
