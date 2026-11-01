package com.ikae.snowthing.domain.member.dto;

import java.util.List;

public record MemberSettingsResponse(
        String publicId,
        String email,
        String nickname,
        String profileImageUrl,
        String bio,
        String departureRegion,
        boolean passwordChangeAvailable,
        List<Preference> resorts,
        List<Preference> ridingStyles) {
    public MemberSettingsResponse {
        resorts = List.copyOf(resorts);
        ridingStyles = List.copyOf(ridingStyles);
    }

    public record Preference(Long id, String name) {}
}
