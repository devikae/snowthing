package com.ikae.snowthing.global.notification;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Discord 웹훅 전송. 알림 실패가 본 기능에 영향을 주지 않도록 전용 풀에서 보내고, 실패는 경고 로그만 남긴다.
 *
 * <p>웹훅 URL은 비밀값이라 로그에 남기지 않는다. 연결 오류 메시지에는 요청 URL이 들어가므로 예외 타입만 기록한다.
 */
@Slf4j
@Component
public class DiscordNotifier {

    static final int MAX_CONTENT_LENGTH = 1900;

    private final RestClient discordRestClient;
    private final DiscordProperties properties;
    private final Executor discordTaskExecutor;

    public DiscordNotifier(
            RestClient discordRestClient,
            DiscordProperties properties,
            @Qualifier("discordTaskExecutor") Executor discordTaskExecutor) {
        this.discordRestClient = discordRestClient;
        this.properties = properties;
        this.discordTaskExecutor = discordTaskExecutor;
    }

    /** #장애 채널 */
    public void alert(String content) {
        send(properties.alertWebhook(), content);
    }

    /** #이벤트 채널 */
    public void event(String content) {
        send(properties.eventWebhook(), content);
    }

    public boolean eventEnabled() {
        return !properties.eventWebhook().isEmpty();
    }

    private void send(String webhook, String content) {
        if (webhook.isEmpty() || content == null || content.isBlank()) {
            return;
        }
        Message message = new Message(truncate(content), AllowedMentions.NONE);
        try {
            discordTaskExecutor.execute(() -> post(webhook, message));
        } catch (RejectedExecutionException e) {
            log.warn("Discord 알림 대기열이 가득 차 알림을 버렸다");
        }
    }

    private void post(String webhook, Message message) {
        try {
            discordRestClient
                    .post()
                    .uri(URI.create(webhook))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("Discord 알림 전송 실패: HTTP {}", e.getStatusCode().value());
        } catch (RuntimeException e) {
            log.warn("Discord 알림 전송 실패: {}", e.getClass().getSimpleName());
        }
    }

    static String truncate(String content) {
        if (content.length() <= MAX_CONTENT_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_CONTENT_LENGTH - 1) + "…";
    }

    record Message(
            String content, @JsonProperty("allowed_mentions") AllowedMentions allowedMentions) {}

    /** parse를 비워 본문에 @everyone, @here, 사용자 멘션이 있어도 알림이 울리지 않게 한다. */
    record AllowedMentions(List<String> parse) {
        static final AllowedMentions NONE = new AllowedMentions(List.of());
    }
}
