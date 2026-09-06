package com.ikae.snowthing.domain.comment.dto;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record PostCommentListResponse(
        String publicId,
        int totalCommentCount,
        List<CommentResponse> comments,
        @JsonSerialize(using = ToStringSerializer.class) Long nextCursor,
        boolean hasNext) {
    public PostCommentListResponse {
        comments = comments == null ? List.of() : List.copyOf(comments);
    }
}
