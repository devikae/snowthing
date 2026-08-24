package com.ikae.snowthing.domain.post.dto;

import lombok.Builder;

@Builder
public record ReactionToggleResponse(
    boolean isToggledOn,
    String type,
    int likeCount,
    int dislikeCount,
    String message
) {}
