package com.ikae.snowthing.domain.post.dto;

import jakarta.validation.constraints.NotNull;

import com.ikae.snowthing.domain.post.entity.ReactionType;

public record PostReactionRequest(
        @NotNull(message = "투표 유형(LIKE/DISLIKE)은 필수 입력값입니다.") ReactionType type) {}
