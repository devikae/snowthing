package com.ikae.snowthing.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(SchedulingConfigTest.TestConfig.class)
class SchedulingConfigTest {

    @Autowired Probe probe;

    @Test
    @DisplayName("다른 TaskScheduler 빈이 여러 개 있어도 @Scheduled는 전용 스케줄러에서 돈다")
    void scheduledTasksRunOnDedicatedScheduler() throws Exception {
        String threadName = probe.threadName.get(5, TimeUnit.SECONDS);

        assertThat(threadName).startsWith("scheduled-");
    }

    @Configuration
    @EnableScheduling
    @Import(SchedulingConfig.class)
    static class TestConfig {

        // STOMP 브로커와 하트비트 스케줄러처럼 이름이 다른 TaskScheduler 빈 두 개.
        @Bean
        TaskScheduler messageBrokerTaskScheduler() {
            return scheduler("broker-");
        }

        @Bean
        TaskScheduler chatHeartbeatTaskScheduler() {
            return scheduler("ws-heartbeat-");
        }

        @Bean
        Probe probe() {
            return new Probe();
        }

        private static ThreadPoolTaskScheduler scheduler(String prefix) {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setThreadNamePrefix(prefix);
            return scheduler;
        }
    }

    static class Probe {
        final CompletableFuture<String> threadName = new CompletableFuture<>();

        @Scheduled(fixedDelay = 60_000L)
        void record() {
            threadName.complete(Thread.currentThread().getName());
        }
    }
}
