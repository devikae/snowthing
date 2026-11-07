package com.ikae.snowthing.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@code @Scheduled} 작업 전용 스케줄러.
 *
 * <p>STOMP 브로커와 하트비트가 TaskScheduler 빈을 따로 등록해서 Spring Boot 기본 스케줄러가 만들어지지 않는다. 그러면 Spring이 스레드 하나짜리
 * 실행기를 만들어 모든 예약 작업(AI 활동, 검색 색인, 사진 정리, 썸네일 등)을 차례로 돌린다. S3나 외부 API에서 한 작업이 멈추면 나머지가 모두 밀린다. 예약 작업
 * 처리기는 이름이 {@code taskScheduler}인 빈을 먼저 찾으므로 이 이름으로 둔다.
 */
@Configuration
public class SchedulingConfig {

    private static final int POOL_SIZE = 3;

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(POOL_SIZE);
        scheduler.setThreadNamePrefix("scheduled-");
        return scheduler;
    }
}
