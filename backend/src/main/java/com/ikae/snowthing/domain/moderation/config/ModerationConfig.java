package com.ikae.snowthing.domain.moderation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ikae.snowthing.domain.moderation.api.ModerationApiClient;
import com.ikae.snowthing.domain.moderation.api.OpenAiModerationClient;

@Configuration
@EnableConfigurationProperties(ModerationProperties.class)
public class ModerationConfig {

    /** 키가 없어도 빈은 만든다. HTTP 클라이언트는 처음 부를 때 만든다. */
    @Bean
    public ModerationApiClient moderationApiClient(ModerationProperties properties) {
        return new OpenAiModerationClient(properties.api());
    }
}
