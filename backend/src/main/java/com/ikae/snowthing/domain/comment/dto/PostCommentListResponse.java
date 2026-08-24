package com.ikae.snowthing.domain.comment.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record PostCommentListResponse(
    String publicId,
    int totalCommentCount,
    List<CommentResponse> comments
) {
    public PostCommentListResponse {
        if (comments == null) {
            comments = List.of();
        } else {
            comments = List.copyOf(comments);
        }
    }
}
