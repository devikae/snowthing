package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;
import java.util.Objects;

import com.ikae.snowthing.domain.post.repository.PostRepository;

/** 관리자 게시글 목록 항목. 회원 내부 ID는 내보내지 않고 작성자 삭제 여부만 계산해 준다. */
public record AdminPostResponse(
        String publicId,
        String title,
        String content,
        String writerNickname,
        String writerEmail,
        boolean isAnonymous,
        boolean isDeleted,
        LocalDateTime deletedAt,
        String deleterNickname,
        String deleterRole,
        boolean isDeletedByAuthor,
        boolean isBlinded,
        String status,
        LocalDateTime createdAt,
        String categoryName) {

    public static AdminPostResponse from(PostRepository.AdminPostProjection p) {
        Long authorId = p.getAuthorMemberId();
        boolean deletedByAuthor =
                authorId != null && Objects.equals(authorId, p.getDeletedByMemberId());
        boolean deleted = Boolean.TRUE.equals(p.getIsDeleted());

        return new AdminPostResponse(
                p.getPublicId(),
                p.getTitle(),
                p.getContent(),
                p.getWriterNickname(),
                p.getWriterEmail(),
                Boolean.TRUE.equals(p.getIsAnonymous()),
                deleted,
                p.getDeletedAt(),
                p.getDeleterNickname(),
                p.getDeleterRole(),
                deletedByAuthor,
                !deleted && "BLOCKED".equals(p.getStatus()),
                p.getStatus(),
                p.getCreatedAt(),
                p.getCategoryName());
    }
}
