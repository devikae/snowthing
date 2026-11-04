package com.ikae.snowthing.domain.aimember;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ikae.snowthing.global.config.AiMemberProperties;

@Configuration
@EnableConfigurationProperties(AiMemberProperties.class)
public class AiMemberConfig {

    public static final String ENABLED_PROPERTY = "snowthing.ai-members.enabled";

    /** compose 호출(최대 15초)과 채팅 지연 전송을 공용 스케줄러 스레드 밖에서 돌린다. 한 스레드라서 AI 행동은 한 번에 하나씩만 실행된다. */
    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnProperty(name = ENABLED_PROPERTY, havingValue = "true")
    public ScheduledExecutorService aiMemberExecutor() {
        return Executors.newSingleThreadScheduledExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "ai-member-");
                    thread.setDaemon(true);
                    return thread;
                });
    }
}
