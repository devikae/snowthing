package com.ikae.snowthing.domain.comment.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ikae.snowthing.domain.comment.entity.Comment;
import com.ikae.snowthing.global.util.WriterDisplayFormatter;

public record CommentResponse(
        Long commentId,
        Long parentId,
        String writerName,
        String content,
        boolean isDeleted,
        LocalDateTime createdAt,
        List<CommentResponse> children) {
    public static CommentResponse from(Comment comment) {
        String writerName =
                WriterDisplayFormatter.format(
                        comment.isAnonymous(), comment.getMember(), comment.getWriterIp());

        String displayContent = comment.isDeleted() ? "삭제된 댓글입니다." : comment.getContent();
        Long parentIdValue = comment.getParent() != null ? comment.getParent().getId() : null;

        return new CommentResponse(
                comment.getId(),
                parentIdValue,
                writerName,
                displayContent,
                comment.isDeleted(),
                comment.getCreatedAt(),
                new ArrayList<>());
    }
}
