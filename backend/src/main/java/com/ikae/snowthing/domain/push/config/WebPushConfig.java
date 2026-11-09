package com.ikae.snowthing.domain.push.config;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.ikae.snowthing.domain.push.service.PushHttpClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 웹 푸시 발송 풀과 HTTP 클라이언트. 푸시 서비스가 느려져도 기존 @Async 풀과 요청 스레드가 밀리지 않게 따로 둔다.
 *
 * <p>대기열이 차면 그 발송은 버리고 경고만 남긴다. 알림은 DB에 이미 저장돼 있어 사이트에서는 보인다. 버리지 않고 예외를 던지면 커밋 뒤 콜백에서 예외가 올라가 이미
 * 끝난 요청이 실패처럼 보인다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(WebPushProperties.class)
public class WebPushConfig {

    @Bean("webPushTaskExecutor")
    public Executor webPushTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("web-push-");
        executor.setRejectedExecutionHandler(
                (task, pool) -> log.warn("웹 푸시 대기열이 가득 차 발송 한 건을 건너뜁니다."));
        executor.initialize();
        return executor;
    }

    @Bean
    public PushHttpClient pushHttpClient(WebPushProperties properties) {
        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(properties.timeout())
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        return (uri, headers, body) -> {
            HttpRequest.Builder request =
                    HttpRequest.newBuilder(uri)
                            .timeout(properties.timeout())
                            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(request::header);
            return client.send(request.build(), HttpResponse.BodyHandlers.discarding())
                    .statusCode();
        };
    }
}
