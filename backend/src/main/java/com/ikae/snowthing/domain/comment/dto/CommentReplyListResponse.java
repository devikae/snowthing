package com.ikae.snowthing.domain.comment.dto;

import java.util.List;

public record CommentReplyListResponse(
        Long rootCommentId,
        long totalReplyCount,
        List<CommentResponse> replies,
        Long nextCursor,
        boolean hasNext) {
    public CommentReplyListResponse {
        replies = replies == null ? List.of() : List.copyOf(replies);
    }
}
