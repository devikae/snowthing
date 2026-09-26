package com.ikae.snowthing.domain.post.dto;

import com.ikae.snowthing.domain.post.entity.ReactionType;

public record ReactionCountMismatch(
        Long postId, String postPublicId, ReactionType type, long storedCount, long actualCount) {}
