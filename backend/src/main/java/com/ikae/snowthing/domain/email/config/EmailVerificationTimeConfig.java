package com.ikae.snowthing.domain.email.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmailVerificationTimeConfig {

    @Bean
    public Clock emailVerificationClock() {
        return Clock.systemDefaultZone();
    }
}
