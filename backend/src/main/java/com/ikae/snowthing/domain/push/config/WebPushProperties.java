package com.ikae.snowthing.domain.push.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 푸시 VAPID 설정. 공개키, 비밀키, subject 중 하나라도 비어 있으면 발송 기능 전체가 꺼진다.
 *
 * <p>키는 base64url(패딩 없음)이다. 공개키는 P-256 비압축 점 65바이트, 비밀키는 스칼라 32바이트다. subject는 푸시 서비스가 문제 있을 때 연락할
 * 주소로 {@code mailto:} 또는 {@code https:} URL이다.
 */
@ConfigurationProperties(prefix = "snowthing.web-push")
public record WebPushProperties(
        String publicKey, String privateKey, String subject, Duration timeout, Duration ttl) {

    public WebPushProperties {
        publicKey = publicKey != null ? publicKey.trim() : "";
        privateKey = privateKey != null ? privateKey.trim() : "";
        subject = subject != null ? subject.trim() : "";
        timeout = timeout != null ? timeout : Duration.ofSeconds(5);
        ttl = ttl != null ? ttl : Duration.ofDays(1);
    }

    public boolean configured() {
        return !publicKey.isEmpty() && !privateKey.isEmpty() && !subject.isEmpty();
    }
}
