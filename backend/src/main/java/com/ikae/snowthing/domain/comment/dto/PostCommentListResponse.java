package com.ikae.snowthing.domain.comment.dto;

import java.util.List;

public record PostCommentListResponse(
        String publicId,
        int totalCommentCount,
        List<CommentResponse> comments,
        Long nextCursor,
        boolean hasNext) {
    public PostCommentListResponse {
        comments = comments == null ? List.of() : List.copyOf(comments);
    }
}
