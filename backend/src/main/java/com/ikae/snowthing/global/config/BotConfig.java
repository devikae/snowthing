package com.ikae.snowthing.global.config;

import java.net.http.HttpClient;
import java.time.Duration;
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

    // 봇은 같은 호스트의 컨테이너라 연결은 금방 된다. OAuth와 카풀 외부 클라이언트의 기본값과 같은 2초로 둔다.
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

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
        // 읽기 제한만 있으면 봇 컨테이너가 연결을 받지 않을 때 연결 단계에서 오래 묶일 수 있다.
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.requestTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
