package com.ikae.snowthing.domain.push.service;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹 푸시 본문 암호화(RFC 8291, aes128gcm)와 VAPID 서명(RFC 8292)에 필요한 P-256 연산. JDK 표준 암호 API만 쓴다.
 *
 * <p>순서: 매 메시지마다 임시 키쌍을 만들고, 브라우저 공개키와 ECDH로 공유 비밀을 얻는다. 브라우저 인증 비밀값(auth)과 HKDF로 섞어 콘텐츠 키(16바이트)와
 * nonce(12바이트)를 만들고 AES-128-GCM으로 한 레코드를 암호화한다. 헤더에는 salt, 레코드 크기, 임시 공개키를 싣는다.
 */
final class WebPushCrypto {

    static final ECParameterSpec P256 = p256();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;
    private static final byte LAST_RECORD_DELIMITER = 0x02;

    private WebPushCrypto() {}

    /** 운영 발송용. salt와 임시 키쌍을 새로 만든다. */
    static byte[] encrypt(byte[] plaintext, byte[] userAgentPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, userAgentPublic, authSecret, salt, generateKeyPair());
    }

    /** RFC 8291 예제처럼 salt와 임시 키쌍을 정해 두고 결과를 비교할 수 있게 연다. */
    static byte[] encrypt(
            byte[] plaintext,
            byte[] userAgentPublic,
            byte[] authSecret,
            byte[] salt,
            KeyPair ephemeral) {
        try {
            byte[] serverPublic = encodePublic((ECPublicKey) ephemeral.getPublic());
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(ephemeral.getPrivate());
            agreement.doPhase(decodePublic(userAgentPublic), true);
            byte[] ecdhSecret = agreement.generateSecret();

            byte[] keyInfo =
                    concat(
                            "WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                            userAgentPublic,
                            serverPublic);
            byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
            byte[] cek =
                    hkdf(
                            salt,
                            ikm,
                            "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII),
                            16);
            byte[] nonce =
                    hkdf(
                            salt,
                            ikm,
                            "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII),
                            12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(cek, "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] ciphertext =
                    cipher.doFinal(concat(plaintext, new byte[] {LAST_RECORD_DELIMITER}));

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + serverPublic.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) serverPublic.length).put(serverPublic);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("웹 푸시 암호화 실패", e);
        }
    }

    static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"), RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 키 생성 실패", e);
        }
    }

    /** 비압축 점(0x04 || X || Y, 65바이트)을 공개키로 읽는다. 곡선 위에 없는 점은 등록 단계에서 막도록 직접 확인한다. */
    static ECPublicKey decodePublic(byte[] uncompressed) {
        if (uncompressed == null || uncompressed.length != 65 || uncompressed[0] != 0x04) {
            throw new IllegalArgumentException("P-256 비압축 공개키가 아닙니다.");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        if (!onCurve(x, y)) {
            throw new IllegalArgumentException("P-256 곡선 위의 점이 아닙니다.");
        }
        try {
            return (ECPublicKey)
                    KeyFactory.getInstance("EC")
                            .generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 공개키를 읽지 못했습니다.", e);
        }
    }

    static ECPrivateKey decodePrivate(byte[] scalar) {
        if (scalar == null || scalar.length != 32) {
            throw new IllegalArgumentException("P-256 비밀키는 32바이트여야 합니다.");
        }
        try {
            return (ECPrivateKey)
                    KeyFactory.getInstance("EC")
                            .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), P256));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("P-256 비밀키를 읽지 못했습니다.", e);
        }
    }

    static byte[] encodePublic(ECPublicKey key) {
        return concat(
                new byte[] {0x04},
                unsigned32(key.getW().getAffineX()),
                unsigned32(key.getW().getAffineY()));
    }

    static byte[] base64UrlDecode(String value) {
        return B64_DECODER.decode(value);
    }

    static String base64UrlEncode(byte[] value) {
        return B64_ENCODER.encodeToString(value);
    }

    /** RFC 5869 HKDF. 출력이 32바이트 이하라 확장 단계는 한 블록(T(1))이면 된다. */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length)
            throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        byte[] okm = hmac(prk, concat(info, new byte[] {0x01}));
        return Arrays.copyOf(okm, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] raw = value.toByteArray();
        if (raw.length == 32) {
            return raw;
        }
        byte[] out = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, out, 0, 32);
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.length, raw.length);
        }
        return out;
    }

    static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }

    /** y^2 = x^3 + ax + b (mod p). */
    private static boolean onCurve(BigInteger x, BigInteger y) {
        BigInteger p = ((ECFieldFp) P256.getCurve().getField()).getP();
        if (x.compareTo(p) >= 0 || y.compareTo(p) >= 0) {
            return false;
        }
        BigInteger left = y.modPow(BigInteger.TWO, p);
        BigInteger right =
                x.pow(3).add(P256.getCurve().getA().multiply(x)).add(P256.getCurve().getB()).mod(p);
        return left.equals(right);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 곡선을 찾지 못했습니다.", e);
        }
    }
}
