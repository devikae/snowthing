package com.ikae.snowthing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class SnowthingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SnowthingApplication.class, args);
    }

}
