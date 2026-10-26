package com.ikae.snowthing.domain.auth.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.member.validation.NicknamePolicy;

@Component
public class SocialNicknameGenerator {

    private static final String DEFAULT_PREFIX = "보더";
    private static final int MAX_PREFIX_LENGTH = 6;
    private static final int SUFFIX_LENGTH = 4;
    private static final char[] ALPHANUMERIC = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();

    public String initialCandidate(String rawNickname) {
        String sanitized = sanitize(rawNickname);
        if (sanitized.length() >= NicknamePolicy.MIN_LENGTH
                && sanitized.length() <= NicknamePolicy.MAX_LENGTH
                && sanitized.matches(NicknamePolicy.REGEXP)) {
            return sanitized;
        }
        return generateWithSuffix(rawNickname);
    }

    public String generateWithSuffix(String rawNickname) {
        String sanitized = sanitize(rawNickname);
        String prefix =
                sanitized.length() >= NicknamePolicy.MIN_LENGTH
                        ? sanitized.substring(0, Math.min(sanitized.length(), MAX_PREFIX_LENGTH))
                        : DEFAULT_PREFIX;
        StringBuilder builder = new StringBuilder(prefix.length() + SUFFIX_LENGTH);
        builder.append(prefix);
        for (int index = 0; index < SUFFIX_LENGTH; index++) {
            builder.append(ALPHANUMERIC[secureRandom.nextInt(ALPHANUMERIC.length)]);
        }
        return builder.toString();
    }

    String sanitize(String rawNickname) {
        if (rawNickname == null || rawNickname.isBlank()) {
            return "";
        }
        return rawNickname.replaceAll("[^a-zA-Z0-9가-힣]", "");
    }
}
