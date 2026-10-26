package com.ikae.snowthing.domain.auth.oauth2;

import com.ikae.snowthing.domain.member.entity.SocialProvider;

public record OAuthUserProfile(
        SocialProvider provider,
        String providerUserId,
        String email,
        boolean emailVerified,
        String nickname) {}
