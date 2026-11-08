package com.ikae.snowthing.global.notification;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Discord 웹훅 설정. 웹훅 URL이 비어 있으면 그 채널 알림은 꺼진다. */
@ConfigurationProperties(prefix = "snowthing.discord")
public record DiscordProperties(String alertWebhook, String eventWebhook, Duration timeout) {

    public DiscordProperties {
        alertWebhook = alertWebhook != null ? alertWebhook.trim() : "";
        eventWebhook = eventWebhook != null ? eventWebhook.trim() : "";
        timeout = timeout != null ? timeout : Duration.ofSeconds(3);
    }
}
