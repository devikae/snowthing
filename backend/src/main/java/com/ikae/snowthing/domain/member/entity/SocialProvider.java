package com.ikae.snowthing.domain.member.entity;

import java.util.Locale;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

public enum SocialProvider {
    GOOGLE,
    NAVER;

    public static SocialProvider from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED);
        }
        try {
            return SocialProvider.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.OAUTH_PROVIDER_UNSUPPORTED, exception);
        }
    }
}
