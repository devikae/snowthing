package com.ikae.snowthing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@EnableJpaAuditing
@SpringBootApplication
public class SnowthingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnowthingApplication.class, args);
    }
}
