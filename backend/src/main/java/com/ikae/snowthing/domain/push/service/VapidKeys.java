package com.ikae.snowthing.domain.push.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.push.config.WebPushProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * VAPID(RFC 8292) 키와 서명. 키가 하나라도 비어 있으면 {@link #enabled()}가 false이고 발송과 구독 등록이 모두 꺼진다.
 *
 * <p>값이 있는데 형식이 틀리거나 공개키와 비밀키가 짝이 아니면 기동을 멈춘다. 잘못된 키로 뜨면 모든 발송이 푸시 서비스에서 403으로 조용히 실패하기 때문이다.
 */
@Slf4j
@Component
public class VapidKeys {

    /** 푸시 서비스는 24시간이 넘는 만료를 거부한다. 시계 차이를 감안해 12시간으로 둔다. */
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private static final String JWT_HEADER =
            WebPushCrypto.base64UrlEncode(
                    "{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));

    private final boolean enabled;
    private final String publicKey;
    private final ECPrivateKey privateKey;
    private final String subject;

    public VapidKeys(WebPushProperties properties) {
        if (!properties.configured()) {
            log.info("웹 푸시 VAPID 키가 없어 휴대폰 알림 발송을 끕니다.");
            this.enabled = false;
            this.publicKey = null;
            this.privateKey = null;
            this.subject = null;
            return;
        }
        String subjectValue = properties.subject();
        if (!subjectValue.startsWith("mailto:") && !subjectValue.startsWith("https://")) {
            throw new IllegalStateException("WEB_PUSH_VAPID_SUBJECT는 mailto: 또는 https:로 시작해야 합니다.");
        }
        ECPublicKey publicKeyValue;
        try {
            publicKeyValue =
                    WebPushCrypto.decodePublic(
                            WebPushCrypto.base64UrlDecode(properties.publicKey()));
            this.privateKey =
                    WebPushCrypto.decodePrivate(
                            WebPushCrypto.base64UrlDecode(properties.privateKey()));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("웹 푸시 VAPID 키 형식이 올바르지 않습니다.", e);
        }
        requirePair(publicKeyValue, privateKey);
        this.enabled = true;
        this.publicKey = WebPushCrypto.base64UrlEncode(WebPushCrypto.encodePublic(publicKeyValue));
        this.subject = subjectValue;
    }

    public boolean enabled() {
        return enabled;
    }

    /** 브라우저 {@code pushManager.subscribe}의 applicationServerKey로 넘길 공개키(base64url). 꺼져 있으면 null. */
    public String publicKey() {
        return publicKey;
    }

    /** {@code Authorization: vapid t=<JWT>, k=<공개키>} 헤더 값. aud는 endpoint의 출처(scheme://host)다. */
    String authorization(URI endpoint, Instant now) {
        if (!enabled) {
            throw new IllegalStateException("웹 푸시가 꺼져 있습니다.");
        }
        String audience = endpoint.getScheme() + "://" + endpoint.getHost();
        String claims =
                "{\"aud\":\""
                        + audience
                        + "\",\"exp\":"
                        + now.plus(TOKEN_LIFETIME).getEpochSecond()
                        + ",\"sub\":\""
                        + subject.replace("\\", "\\\\").replace("\"", "\\\"")
                        + "\"}";
        String signingInput =
                JWT_HEADER
                        + "."
                        + WebPushCrypto.base64UrlEncode(claims.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sign(privateKey, signingInput.getBytes(StandardCharsets.US_ASCII));
        return "vapid t="
                + signingInput
                + "."
                + WebPushCrypto.base64UrlEncode(signature)
                + ", k="
                + publicKey;
    }

    /** ES256 JWT 서명은 DER가 아니라 r || s 64바이트다. JDK의 P1363 형식이 그대로 그 모양이다. */
    private static byte[] sign(ECPrivateKey key, byte[] data) {
        try {
            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(key);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID 서명 실패", e);
        }
    }

    private static void requirePair(ECPublicKey publicKey, ECPrivateKey privateKey) {
        byte[] probe = "snowthing-vapid-pair-check".getBytes(StandardCharsets.US_ASCII);
        try {
            Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
            verifier.initVerify(publicKey);
            verifier.update(probe);
            if (!verifier.verify(sign(privateKey, probe))) {
                throw new IllegalStateException("웹 푸시 VAPID 공개키와 비밀키가 짝이 아닙니다.");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("웹 푸시 VAPID 키를 확인하지 못했습니다.", e);
        }
    }
}
