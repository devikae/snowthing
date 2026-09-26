package com.ikae.snowthing.domain.post.dto;

import com.ikae.snowthing.domain.post.entity.ReactionType;

public record ReactionResponse(
        boolean active,
        ReactionType type,
        int likeCount,
        int dislikeCount,
        boolean changed,
        String message) {}
