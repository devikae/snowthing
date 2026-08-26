package com.ikae.snowthing.domain.member.dto;

import com.ikae.snowthing.domain.member.entity.RidingStyle;

public record RidingStyleResponse(Long id, String styleName, String description) {
    public static RidingStyleResponse from(RidingStyle ridingStyle) {
        return new RidingStyleResponse(
                ridingStyle.getId(), ridingStyle.getStyleName(), ridingStyle.getDescription());
    }
}
