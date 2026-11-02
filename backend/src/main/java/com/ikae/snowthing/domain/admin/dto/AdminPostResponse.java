package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.post.repository.PostRepository;

public record AdminPostResponse(
        String publicId,
        String title,
        String content,
        String writerNickname,
        String writerEmail,
        boolean isAnonymous,
        boolean isDeleted,
        LocalDateTime deletedAt,
        boolean isBlinded,
        LocalDateTime createdAt,
        String categoryName) {

    public static AdminPostResponse from(PostRepository.AdminPostProjection p) {
        return new AdminPostResponse(
                p.getPublicId(),
                p.getTitle(),
                p.getContent(),
                p.getWriterNickname(),
                p.getWriterEmail(),
                Boolean.TRUE.equals(p.getIsAnonymous()),
                Boolean.TRUE.equals(p.getIsDeleted()),
                p.getDeletedAt(),
                Boolean.TRUE.equals(p.getIsBlinded()),
                p.getCreatedAt(),
                p.getCategoryName());
    }
}
