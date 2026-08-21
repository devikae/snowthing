package com.ikae.snowthing.domain.post.event;

import com.ikae.snowthing.domain.post.entity.ReactionType;

public record PostReactionEvent(
    Long postId,
    ReactionType type
) {}
