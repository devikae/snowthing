package com.ikae.snowthing.domain.push.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** 브라우저 쪽 복호화(RFC 8291)를 테스트에서 흉내 낸다. 서버 구현과 독립적으로 다시 썼다. */
public final class WebPushTestDecryptor {

    private WebPushTestDecryptor() {}

    public static byte[] decrypt(byte[] message, KeyPair browser, byte[] authSecret) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message);
            byte[] salt = new byte[16];
            buffer.get(salt);
            buffer.getInt();
            byte[] serverPublic = new byte[buffer.get() & 0xff];
            buffer.get(serverPublic);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            byte[] browserPublic = WebPushCrypto.encodePublic((ECPublicKey) browser.getPublic());
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(browser.getPrivate());
            agreement.doPhase(WebPushCrypto.decodePublic(serverPublic), true);
            byte[] ecdh = agreement.generateSecret();

            byte[] prkKey = hmac(authSecret, ecdh);
            byte[] ikm =
                    hmac(
                            prkKey,
                            WebPushCrypto.concat(
                                    "WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
                                    browserPublic,
                                    serverPublic,
                                    new byte[] {1}));
            byte[] prk = hmac(salt, ikm);
            byte[] cek =
                    Arrays.copyOf(
                            hmac(
                                    prk,
                                    "Content-Encoding: aes128gcm\0\1"
                                            .getBytes(StandardCharsets.US_ASCII)),
                            16);
            byte[] nonce =
                    Arrays.copyOf(
                            hmac(
                                    prk,
                                    "Content-Encoding: nonce\0\1"
                                            .getBytes(StandardCharsets.US_ASCII)),
                            12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(cek, "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] padded = cipher.doFinal(ciphertext);
            int end = padded.length - 1;
            while (end >= 0 && padded[end] == 0) {
                end--;
            }
            if (padded[end] != 0x02) {
                throw new IllegalStateException("마지막 레코드 구분자가 없습니다.");
            }
            return Arrays.copyOf(padded, end);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
