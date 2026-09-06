package com.ikae.snowthing.domain.comment.dto;

import java.time.LocalDateTime;

public record CommentUpdateResponse(Long commentId, String content, LocalDateTime updatedAt) {}
