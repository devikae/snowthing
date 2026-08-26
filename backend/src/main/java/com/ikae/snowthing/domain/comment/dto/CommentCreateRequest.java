package com.ikae.snowthing.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Builder;

@Builder
public record CommentCreateRequest(
        Long parentId,
        @NotBlank(message = "댓글 내용은 필수 입력값입니다.")
                @Size(max = 1000, message = "댓글은 최대 1000자까지 입력 가능합니다.")
                String content,
        boolean isAnonymous,
        String anonymousPassword) {}
