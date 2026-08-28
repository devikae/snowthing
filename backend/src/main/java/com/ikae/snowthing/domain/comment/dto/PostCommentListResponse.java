package com.ikae.snowthing.domain.comment.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record PostCommentListResponse(
        String publicId, int totalCommentCount, List<CommentResponse> comments) {
    public PostCommentListResponse {
        if (comments == null) {
            comments = List.of();
        } else {
            comments = List.copyOf(comments);
        }
    }
}
