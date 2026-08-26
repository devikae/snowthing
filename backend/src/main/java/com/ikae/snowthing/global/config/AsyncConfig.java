package com.ikae.snowthing.global.config;

import java.util.concurrent.Executor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import lombok.extern.slf4j.Slf4j;

/**
 * 비동기 실행 및 가상 스레드(Virtual Thread) 설정
 *
 * <p>[적용 범위 및 아키텍처 동작 원리]: 1. spring.threads.virtual.enabled=true: - 내장 Tomcat 서블릿 컨테이너에 적용되어 모든
 * 인바운드 HTTP 요청마다 가상 스레드를 할당합니다. - Spring Boot 자동 구성(TaskExecutionAutoConfiguration)의 기본
 * applicationTaskExecutor가 가상 스레드를 활성화합니다. 2. AsyncConfigurer 커스텀 구현: - @Async 비동기 메서드
 * 실행기(Executor)에 명시적인 가상 스레드 네이밍 prefix("async-vt-")를 부여하여 APM/로그 추적성을 확보합니다. - void 반환 비동기 메서드에서
 * 발생하는 미처리 예외(Uncaught Exception)를 일원화하여 로깅하는 핸들러를 등록합니다.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("async-vt-");
        executor.setVirtualThreads(true);
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
