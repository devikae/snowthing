package com.ikae.snowthing.domain.push.service;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.push.config.WebPushProperties;
import com.ikae.snowthing.domain.push.entity.PushSubscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 구독 하나에 암호화한 푸시 한 건을 보낸다. 결과에 따라 호출자가 구독을 지우거나 최근 사용 시각을 고친다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushSender {

    public enum Outcome {
        DELIVERED,
        /** 푸시 서비스가 더는 없는 구독이라고 답했다(404, 410). 지운다. */
        GONE,
        /** 일시적 실패. 구독은 남겨 둔다. */
        FAILED
    }

    private final VapidKeys vapidKeys;
    private final PushHttpClient httpClient;
    private final WebPushProperties properties;
    private final Clock clock;

    public Outcome send(PushSubscription subscription, byte[] payload) {
        URI endpoint = PushEndpointPolicy.parse(subscription.getEndpoint());
        if (endpoint == null) {
            return Outcome.GONE;
        }
        byte[] body;
        try {
            body =
                    WebPushCrypto.encrypt(
                            payload,
                            WebPushCrypto.base64UrlDecode(subscription.getP256dh()),
                            WebPushCrypto.base64UrlDecode(subscription.getAuth()));
        } catch (IllegalArgumentException e) {
            // 등록할 때 검증하므로 여기 오면 쓸 수 없는 행이다.
            return Outcome.GONE;
        }
        Map<String, String> headers =
                Map.of(
                        "Authorization",
                        vapidKeys.authorization(endpoint, Instant.now(clock)),
                        "TTL",
                        String.valueOf(properties.ttl().toSeconds()),
                        "Content-Encoding",
                        "aes128gcm",
                        "Content-Type",
                        "application/octet-stream",
                        "Urgency",
                        "normal");
        try {
            int status = httpClient.post(endpoint, headers, body);
            if (status >= 200 && status < 300) {
                return Outcome.DELIVERED;
            }
            if (status == 404 || status == 410) {
                return Outcome.GONE;
            }
            log.warn("웹 푸시 발송 실패 host={} status={}", endpoint.getHost(), status);
            return Outcome.FAILED;
        } catch (IOException e) {
            log.warn(
                    "웹 푸시 발송 실패 host={} cause={}",
                    endpoint.getHost(),
                    e.getClass().getSimpleName());
            return Outcome.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.FAILED;
        }
    }
}
