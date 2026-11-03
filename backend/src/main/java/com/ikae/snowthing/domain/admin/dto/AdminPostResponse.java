package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.Objects;

import com.ikae.snowthing.domain.post.repository.PostRepository;

public record AdminPostResponse(
        String publicId,
        String title,
        String content,
        Long authorMemberId,
        String writerNickname,
        String writerEmail,
        boolean isAnonymous,
        boolean isDeleted,
        LocalDateTime deletedAt,
        Long deletedByMemberId,
        String deleterNickname,
        String deleterEmail,
        String deleterRole,
        boolean isDeletedByAuthor,
        boolean isBlinded,
        LocalDateTime createdAt,
        String categoryName) {

    public static AdminPostResponse from(PostRepository.AdminPostProjection p) {
        Long authorId = p.getAuthorMemberId();
        Long deleterId = p.getDeletedByMemberId();
        boolean deletedByAuthor = (authorId != null && Objects.equals(authorId, deleterId));

        return new AdminPostResponse(
                p.getPublicId(),
                p.getTitle(),
                p.getContent(),
                authorId,
                p.getWriterNickname(),
                p.getWriterEmail(),
                Boolean.TRUE.equals(p.getIsAnonymous()),
                Boolean.TRUE.equals(p.getIsDeleted()),
                p.getDeletedAt(),
                deleterId,
                p.getDeleterNickname(),
                p.getDeleterEmail(),
                p.getDeleterRole(),
                deletedByAuthor,
                Boolean.TRUE.equals(p.getIsBlinded()),
                p.getCreatedAt(),
                p.getCategoryName());
    }
}
