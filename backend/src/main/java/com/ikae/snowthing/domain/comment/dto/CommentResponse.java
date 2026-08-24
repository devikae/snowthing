package com.ikae.snowthing.domain.comment.dto;

import com.ikae.snowthing.domain.comment.entity.Comment;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder
public record CommentResponse(
    Long commentId,
    Long parentId,
    String writerName,
    String content,
    boolean isDeleted,
    LocalDateTime createdAt,
    List<CommentResponse> children
) {
    public static CommentResponse from(Comment comment) {
        String writerName = comment.isAnonymous()
            ? "익명 (" + maskIp(comment.getWriterIp()) + ")"
            : (comment.getMember() != null ? comment.getMember().getNickname() : "알 수 없음");

        String displayContent = comment.isDeleted() ? "삭제된 댓글입니다." : comment.getContent();
        Long parentIdValue = comment.getParent() != null ? comment.getParent().getId() : null;

        return CommentResponse.builder()
            .commentId(comment.getId())
            .parentId(parentIdValue)
            .writerName(writerName)
            .content(displayContent)
            .isDeleted(comment.isDeleted())
            .createdAt(comment.getCreatedAt())
            .children(new ArrayList<>())
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
