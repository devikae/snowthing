package com.ikae.snowthing.global.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class BotConfig {

    @Bean("botTaskExecutor")
    public Executor botTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("chat-bot-");
        executor.initialize();
        return executor;
    }

    @Bean
    public RestClient botRestClient(BotProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(properties.requestTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
