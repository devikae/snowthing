package com.ikae.snowthing.domain.comment.dto;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record CommentReplyListResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long rootCommentId,
        long totalReplyCount,
        List<CommentResponse> replies,
        @JsonSerialize(using = ToStringSerializer.class) Long nextCursor,
        boolean hasNext) {
    public CommentReplyListResponse {
        replies = replies == null ? List.of() : List.copyOf(replies);
    }
}
