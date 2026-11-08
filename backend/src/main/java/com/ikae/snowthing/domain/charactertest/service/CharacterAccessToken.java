package com.ikae.snowthing.domain.charactertest.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 비회원 접근 토큰. 임의 256비트라 추측할 수 없어 느린 해시 없이 SHA-256으로 저장한다. BCrypt는 사람이 고른 낮은 엔트로피 비밀번호를 위한 것이고, 여기서는
 * UNIQUE 인덱스로 찾아야 해서 쓸 수 없다.
 */
final class CharacterAccessToken {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CharacterAccessToken() {}

    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        }
    }
}
