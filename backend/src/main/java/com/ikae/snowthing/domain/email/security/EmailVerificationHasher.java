package com.ikae.snowthing.domain.email.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.email.entity.EmailVerificationPurpose;

@Component
public class EmailVerificationHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MINIMUM_SECRET_LENGTH = 32;

    private final byte[] secret;

    public EmailVerificationHasher(@Value("${snowthing.email.verification.secret}") String secret) {
        if (secret == null || secret.length() < MINIMUM_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Email verification secret must contain at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8).clone();
    }

    public String digestCode(
            String requestId, String email, EmailVerificationPurpose purpose, String code) {
        return digest(requestId + ":" + email + ":" + purpose.name() + ":" + code);
    }

    public String digestToken(String token) {
        return digest("TOKEN:" + token);
    }

    public boolean matches(String expectedHex, String actualHex) {
        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.US_ASCII),
                actualHex.getBytes(StandardCharsets.US_ASCII));
    }

    private String digest(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC algorithm is unavailable", exception);
        }
    }
}
