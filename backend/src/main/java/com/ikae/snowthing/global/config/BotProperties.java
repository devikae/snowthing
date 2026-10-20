package com.ikae.snowthing.global.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snowthing.bot")
public record BotProperties(
        boolean enabled,
        URI baseUrl,
        String internalSecret,
        String delegationSecret,
        Duration delegationTtl,
        Duration requestTimeout) {

    public BotProperties {
        baseUrl = baseUrl != null ? baseUrl : URI.create("http://127.0.0.1:3100");
        internalSecret = internalSecret != null ? internalSecret : "";
        delegationSecret = delegationSecret != null ? delegationSecret : "";
        delegationTtl = delegationTtl != null ? delegationTtl : Duration.ofSeconds(60);
        requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(15);
    }
}
