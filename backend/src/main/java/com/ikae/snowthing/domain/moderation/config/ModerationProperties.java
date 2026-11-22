package com.ikae.snowthing.domain.moderation.config;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 자동 검열 설정 (IKA-80).
 *
 * @param enabled 기능 전체 스위치. 꺼져 있으면 대기열에 넣지 않고 스케줄러도 만들지 않는다. 기본 꺼짐
 * @param schedulerEnabled 스케줄러 빈을 만들지. 테스트는 끄고 처리기를 직접 부른다
 * @param pollIntervalMs 스케줄러 주기 (기본 1분)
 * @param batchSize 한 번에 가져오는 줄 수
 * @param lease 가져간 줄의 임대 시간. 처리 중 서버가 죽으면 이 시간이 지난 뒤 다시 가져간다
 * @param maxAttempts 이 횟수만큼 실패하면 FAILED로 두고 검토 대기로 보낸다
 * @param retryBaseDelay 첫 재시도 지연. 이후 두 배씩 늘린다
 * @param retryMaxDelay 재시도 지연 상한
 * @param hourlyAlertThreshold 한 시간 자동 숨김이 이 수를 넘으면 Discord #장애로 알린다
 * @param rules 1단계 규칙
 * @param api 2단계 OpenAI Moderation API
 */
@ConfigurationProperties(prefix = "snowthing.moderation")
public record ModerationProperties(
        boolean enabled,
        Boolean schedulerEnabled,
        long pollIntervalMs,
        int batchSize,
        Duration lease,
        int maxAttempts,
        Duration retryBaseDelay,
        Duration retryMaxDelay,
        int hourlyAlertThreshold,
        Rules rules,
        Api api) {

    public ModerationProperties {
        schedulerEnabled = schedulerEnabled == null || schedulerEnabled;
        pollIntervalMs = pollIntervalMs > 0 ? pollIntervalMs : 60_000L;
        batchSize = batchSize > 0 ? batchSize : 50;
        lease = lease != null ? lease : Duration.ofMinutes(5);
        maxAttempts = maxAttempts > 0 ? maxAttempts : 5;
        retryBaseDelay = retryBaseDelay != null ? retryBaseDelay : Duration.ofMinutes(1);
        retryMaxDelay = retryMaxDelay != null ? retryMaxDelay : Duration.ofMinutes(30);
        hourlyAlertThreshold = hourlyAlertThreshold > 0 ? hourlyAlertThreshold : 10;
        rules = rules != null ? rules : new Rules(null, null, 0, 0, null, 0, 0);
        api = api != null ? api : new Api(false, null, null, null, 0, 0, null, 0, null, null);
    }

    /**
     * @param enabled 1단계 스위치
     * @param duplicateWindow 같은 내용 반복을 보는 시간 (이 줄의 작성 시각 기준 앞쪽)
     * @param duplicateMinCount 이 줄을 포함해 같은 작성자나 같은 IP의 같은 내용이 이 수 이상이면 반복으로 본다
     * @param duplicateMinLength 정리한 글자 수가 이보다 짧으면 반복 규칙을 보지 않는다("감사합니다" 같은 짧은 댓글)
     * @param newAccountAge 가입 뒤 이 시간 안이면 새 계정
     * @param newAccountMinLinks 새 계정 글의 외부 링크가 이 수 이상이면 걸린다
     * @param shortTextLength 규칙에 하나도 걸리지 않은 글의 정리한 글자 수가 이보다 짧으면 2단계를 건너뛴다
     */
    public record Rules(
            Boolean enabled,
            Duration duplicateWindow,
            int duplicateMinCount,
            int duplicateMinLength,
            Duration newAccountAge,
            int newAccountMinLinks,
            int shortTextLength) {

        public Rules {
            enabled = enabled == null || enabled;
            duplicateWindow = duplicateWindow != null ? duplicateWindow : Duration.ofMinutes(10);
            duplicateMinCount = duplicateMinCount > 1 ? duplicateMinCount : 3;
            duplicateMinLength = duplicateMinLength > 0 ? duplicateMinLength : 10;
            newAccountAge = newAccountAge != null ? newAccountAge : Duration.ofHours(24);
            newAccountMinLinks = newAccountMinLinks > 0 ? newAccountMinLinks : 3;
            shortTextLength = shortTextLength > 0 ? shortTextLength : 20;
        }
    }

    /**
     * @param enabled 2단계 스위치. 기본 꺼짐. 개인정보처리방침에 국외 이전을 더한 뒤 켠다
     * @param apiKey {@code SNOWTHING_OPENAI_API_KEY}. 비어 있으면 2단계를 건너뛰고 규칙만 돈다
     * @param baseUrl OpenAI API 주소
     * @param model 모델 이름
     * @param hideThreshold {@code hideCategories} 점수가 이 값 이상이면 숨긴다
     * @param reviewThreshold 어느 카테고리든 점수가 이 값 이상이면 검토 대기
     * @param hideCategories 숨김 판정에 쓰는 카테고리
     * @param maxInputsPerCall 한 요청에 묶는 글 수
     * @param connectTimeout 연결 제한 시간
     * @param readTimeout 응답 제한 시간
     */
    public record Api(
            boolean enabled,
            String apiKey,
            String baseUrl,
            String model,
            double hideThreshold,
            double reviewThreshold,
            List<String> hideCategories,
            int maxInputsPerCall,
            Duration connectTimeout,
            Duration readTimeout) {

        public Api {
            apiKey = apiKey != null ? apiKey.strip() : "";
            baseUrl =
                    baseUrl == null || baseUrl.isBlank()
                            ? "https://api.openai.com/v1"
                            : baseUrl.strip();
            model = model == null || model.isBlank() ? "omni-moderation-latest" : model.strip();
            hideThreshold = hideThreshold > 0 ? hideThreshold : 0.9;
            reviewThreshold = reviewThreshold > 0 ? reviewThreshold : 0.4;
            hideCategories =
                    hideCategories == null || hideCategories.isEmpty()
                            ? List.of("sexual", "sexual/minors", "illicit", "illicit/violent")
                            : List.copyOf(hideCategories);
            maxInputsPerCall = maxInputsPerCall > 0 ? Math.min(maxInputsPerCall, 32) : 20;
            connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(3);
            readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(15);
        }

        /** 스위치가 켜져 있고 키가 있을 때만 2단계를 부른다. */
        public boolean active() {
            return enabled && !apiKey.isEmpty();
        }

        public Set<String> hideCategorySet() {
            return hideCategories.stream().map(String::strip).collect(Collectors.toSet());
        }

        /** 키가 로그나 예외에 찍히지 않게 한다. */
        @Override
        public String toString() {
            return "Api[enabled=" + enabled + ", model=" + model + ", apiKey=***]";
        }
    }
}
