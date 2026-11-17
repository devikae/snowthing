package com.ikae.snowthing.domain.dmmail.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 쪽지 도착 메일 설정 (IKA-88). 적재와 발송을 따로 켠다. 운영은 둘 다 꺼진 채 배포하고, 적재를 먼저 켜서 확인한 뒤 발송을 켠다.
 *
 * @param enqueueEnabled 쪽지 트랜잭션에서 outbox 행을 쓸지
 * @param sendEnabled 워커가 SES를 부를지. false면 스케줄러 빈을 만들지 않는다
 * @param deliveryMode {@code outbox}(기본) 또는 {@code sync-baseline}. 후자는 성능 비교 기준선 측정 전용으로, 쪽지 트랜잭션
 *     안에서 바로 SES를 부른다. 운영에서 쓰지 않는다
 * @param cooldown 같은 받는 사람에게 메일을 다시 보내기까지 최소 간격. 그동안 온 쪽지는 모아서 한 통으로 보낸다
 * @param deadline 쪽지 생성 후 이 시간이 지나면 보내지 않는다(SKIPPED, EXPIRED)
 * @param lease 선점한 작업의 소유 유지 시간. SES 호출 한 번의 전체 제한 시간보다 충분히 길어야 한다
 * @param maxAttempts 미수락이 확실한 실패의 최대 시도 횟수
 * @param retryBaseDelay 재시도 백오프 기본값
 * @param retryMaxDelay 재시도 백오프 상한
 * @param recipientsPerPoll 폴링 한 번에 처리할 최대 받는 사람 수
 * @param retention ACCEPTED, SKIPPED 행 보관 기간. UNKNOWN, FAILED는 운영 확인용으로 지우지 않는다
 * @param linkBaseUrl 메일 속 쪽지함 링크의 기준 주소
 * @param ses SES 호출 제한 시간과 테스트용 endpoint
 */
@ConfigurationProperties(prefix = "snowthing.dm-mail")
public record DmMailProperties(
        boolean enqueueEnabled,
        boolean sendEnabled,
        String deliveryMode,
        Duration cooldown,
        Duration deadline,
        Duration lease,
        int maxAttempts,
        Duration retryBaseDelay,
        Duration retryMaxDelay,
        int recipientsPerPoll,
        Duration retention,
        String linkBaseUrl,
        Ses ses) {

    public static final String SYNC_BASELINE = "sync-baseline";

    public DmMailProperties {
        deliveryMode = deliveryMode == null || deliveryMode.isBlank() ? "outbox" : deliveryMode;
        cooldown = cooldown != null ? cooldown : Duration.ofMinutes(10);
        deadline = deadline != null ? deadline : Duration.ofHours(24);
        lease = lease != null ? lease : Duration.ofSeconds(60);
        maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
        retryBaseDelay = retryBaseDelay != null ? retryBaseDelay : Duration.ofSeconds(30);
        retryMaxDelay = retryMaxDelay != null ? retryMaxDelay : Duration.ofMinutes(10);
        recipientsPerPoll = recipientsPerPoll > 0 ? recipientsPerPoll : 5;
        retention = retention != null ? retention : Duration.ofDays(30);
        linkBaseUrl = linkBaseUrl != null ? linkBaseUrl.trim() : "http://localhost:3000";
        ses = ses != null ? ses : new Ses(null, null, null);
    }

    public boolean syncBaseline() {
        return SYNC_BASELINE.equals(deliveryMode);
    }

    /**
     * @param attemptTimeout HTTP 시도 한 번의 제한 시간
     * @param callTimeout 호출 전체 제한 시간. SDK 재시도를 끄므로 사실상 시도 제한과 같다
     * @param endpoint 비어 있으면 실제 SES. 로컬 실험과 테스트에서 가짜 SES 주소를 넣는다
     */
    public record Ses(Duration attemptTimeout, Duration callTimeout, String endpoint) {
        public Ses {
            attemptTimeout = attemptTimeout != null ? attemptTimeout : Duration.ofSeconds(3);
            callTimeout = callTimeout != null ? callTimeout : Duration.ofSeconds(5);
            endpoint = endpoint != null ? endpoint.trim() : "";
        }
    }
}
