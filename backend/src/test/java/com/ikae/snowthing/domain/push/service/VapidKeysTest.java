package com.ikae.snowthing.domain.push.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.push.config.WebPushProperties;

class VapidKeysTest {

    @Test
    @DisplayName("키나 subject가 하나라도 비어 있으면 꺼진 상태로 뜬다")
    void blankDisables() {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        assertThat(new VapidKeys(properties("", "", "")).enabled()).isFalse();
        VapidKeys noSubject = new VapidKeys(properties(publicOf(pair), privateOf(pair), " "));
        assertThat(noSubject.enabled()).isFalse();
        assertThat(noSubject.publicKey()).isNull();
    }

    @Test
    @DisplayName("JWT는 endpoint 출처를 aud로 담고 공개키로 검증되는 ES256 서명을 단다")
    void signsVerifiableJwt() throws Exception {
        KeyPair pair = WebPushCrypto.generateKeyPair();
        VapidKeys keys =
                new VapidKeys(
                        properties(publicOf(pair), privateOf(pair), "mailto:ops@example.com"));
        Instant now = Instant.parse("2026-11-09T00:00:00Z");

        String header =
                keys.authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc"), now);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + publicOf(pair));
        String jwt = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = jwt.split("\\.");
        String claims = new String(WebPushCrypto.base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims)
                .contains("\"aud\":\"https://fcm.googleapis.com\"")
                .contains("\"exp\":" + now.plusSeconds(12 * 3600).getEpochSecond())
                .contains("\"sub\":\"mailto:ops@example.com\"");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(pair.getPublic());
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(WebPushCrypto.base64UrlDecode(parts[2]))).isTrue();
    }

    @Test
    @DisplayName("공개키와 비밀키가 짝이 아니거나 subject 형식이 틀리면 기동을 멈춘다")
    void rejectsMisconfiguration() {
        KeyPair one = WebPushCrypto.generateKeyPair();
        KeyPair other = WebPushCrypto.generateKeyPair();
        assertThatThrownBy(
                        () ->
                                new VapidKeys(
                                        properties(
                                                publicOf(one),
                                                privateOf(other),
                                                "mailto:ops@example.com")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                new VapidKeys(
                                        properties(publicOf(one), privateOf(one), "ops@example")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new VapidKeys(properties("abc", privateOf(one), "mailto:a@b.c")))
                .isInstanceOf(IllegalStateException.class);
    }

    static WebPushProperties properties(String publicKey, String privateKey, String subject) {
        return new WebPushProperties(publicKey, privateKey, subject, null, null);
    }

    static String publicOf(KeyPair pair) {
        return WebPushCrypto.base64UrlEncode(
                WebPushCrypto.encodePublic((ECPublicKey) pair.getPublic()));
    }

    static String privateOf(KeyPair pair) {
        byte[] raw = ((ECPrivateKey) pair.getPrivate()).getS().toByteArray();
        byte[] scalar = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, scalar, 32 - copy, copy);
        return WebPushCrypto.base64UrlEncode(scalar);
    }
}
