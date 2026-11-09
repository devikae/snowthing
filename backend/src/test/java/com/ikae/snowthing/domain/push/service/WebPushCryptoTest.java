package com.ikae.snowthing.domain.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    @Test
    @DisplayName("RFC 8291 5절 예제와 같은 암호문을 만든다")
    void matchesRfc8291Example() {
        byte[] uaPublic =
                b64(
                        "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] authSecret = b64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] salt = b64("DGv6ra1nlYgDCS1FRnbzlw");
        ECPublicKey asPublic =
                WebPushCrypto.decodePublic(
                        b64(
                                "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));
        ECPrivateKey asPrivate =
                WebPushCrypto.decodePrivate(b64("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw"));

        byte[] encrypted =
                WebPushCrypto.encrypt(
                        "When I grow up, I want to be a watermelon"
                                .getBytes(StandardCharsets.UTF_8),
                        uaPublic,
                        authSecret,
                        salt,
                        new KeyPair(asPublic, asPrivate));

        assertThat(WebPushCrypto.base64UrlEncode(encrypted))
                .isEqualTo(
                        "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }

    @Test
    @DisplayName("임의 키로 암호화한 본문을 브라우저 비밀키로 풀면 원문이 나온다")
    void roundTrip() {
        KeyPair browser = WebPushCrypto.generateKeyPair();
        byte[] authSecret = Arrays.copyOf("0123456789abcdef".getBytes(), 16);
        byte[] plaintext = "{\"title\":\"눈팅\"}".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted =
                WebPushCrypto.encrypt(
                        plaintext,
                        WebPushCrypto.encodePublic((ECPublicKey) browser.getPublic()),
                        authSecret);

        assertThat(WebPushTestDecryptor.decrypt(encrypted, browser, authSecret))
                .isEqualTo(plaintext);
        // 매번 salt와 임시 키가 달라 같은 본문도 암호문이 다르다.
        assertThat(
                        WebPushCrypto.encrypt(
                                plaintext,
                                WebPushCrypto.encodePublic((ECPublicKey) browser.getPublic()),
                                authSecret))
                .isNotEqualTo(encrypted);
    }

    @Test
    @DisplayName("길이가 틀리거나 곡선 위에 없는 공개키는 거절한다")
    void rejectsInvalidPublicKey() {
        assertThatThrownBy(() -> WebPushCrypto.decodePublic(new byte[64]))
                .isInstanceOf(IllegalArgumentException.class);
        byte[] offCurve = new byte[65];
        offCurve[0] = 0x04;
        offCurve[64] = 0x01;
        assertThatThrownBy(() -> WebPushCrypto.decodePublic(offCurve))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] b64(String value) {
        return WebPushCrypto.base64UrlDecode(value);
    }
}
