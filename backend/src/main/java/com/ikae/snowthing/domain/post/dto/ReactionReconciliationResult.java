package com.ikae.snowthing.domain.post.dto;

import com.ikae.snowthing.domain.post.entity.ReactionType;

public record ReactionReconciliationResult(
        String postPublicId,
        ReactionType type,
        long beforeCount,
        long afterCount,
        boolean changed) {}
