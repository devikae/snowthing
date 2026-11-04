package com.ikae.snowthing.global.notification;

import java.net.http.HttpClient;
import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/** 알림 전송은 기존 @Async 풀과 나눠, Discord가 느려져도 다른 비동기 작업이 밀리지 않게 한다. */
@Configuration
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordConfig {

    @Bean("discordTaskExecutor")
    public Executor discordTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("discord-");
        executor.initialize();
        return executor;
    }

    @Bean
    public RestClient discordRestClient(DiscordProperties properties) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
