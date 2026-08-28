package com.ikae.snowthing.global.config;

import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.extern.slf4j.Slf4j;

/**
 * 비동기 실행을 위한 표준 플랫폼 스레드 풀(Platform Thread Pool) 설정
 *
 * <p>[아키텍처 설계 배경]: 1. 가상 스레드 조기 최적화(Premature Optimization) 배제: - BCrypt 암호화 연산 병목, HikariCP 커넥션 풀
 * 고갈 위험, Thread Pinning 방지 등을 고려하여 안정적인 고정 스레드 풀 모델 채택. 2. ThreadPoolTaskExecutor 구성: - Core Pool
 * Size: 8 (CPU 코어 기반) - Max Pool Size: 16 - Queue Capacity: 100 - Thread Name Prefix:
 * "async-worker-" 3. 예외 핸들링: - void 반환 비동기 메서드의 미처리 예외(Uncaught Exception) 로깅 일원화.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-worker-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error(
                        "[Async Error] 비동기 메서드 '{}' 실행 중 예외 발생: {}",
                        method.getName(),
                        throwable.getMessage(),
                        throwable);
    }
}
