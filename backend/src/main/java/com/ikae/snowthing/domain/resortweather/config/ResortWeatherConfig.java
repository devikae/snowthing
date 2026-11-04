package com.ikae.snowthing.domain.resortweather.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ResortWeatherProperties.class)
public class ResortWeatherConfig {

    public static final String REFRESH_EXECUTOR = "resortWeatherRefreshExecutor";

    private static final int REFRESH_THREADS = 2;
    private static final int REFRESH_QUEUE_SIZE = 13;

    /** 미리 갱신 전용 풀. 외부 HTTP 대기를 공용 ForkJoinPool에 섞지 않는다. 큐가 차면 제출이 거절되고, 갱신을 건너뛴 채 기존 값을 계속 쓴다. */
    @Bean(name = REFRESH_EXECUTOR, destroyMethod = "shutdownNow")
    public ExecutorService resortWeatherRefreshExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(
                REFRESH_THREADS,
                REFRESH_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(REFRESH_QUEUE_SIZE),
                runnable -> {
                    Thread thread =
                            new Thread(runnable, "resort-weather-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }
}
