package com.ikae.snowthing.domain.email.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@ConditionalOnProperty(name = "snowthing.mail.provider", havingValue = "ses")
public class SesMailConfig {

    private static final Duration API_CALL_ATTEMPT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration API_CALL_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public SesV2Client sesV2Client(@Value("${snowthing.mail.region}") String region) {
        ClientOverrideConfiguration overrideConfiguration =
                ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(API_CALL_ATTEMPT_TIMEOUT)
                        .apiCallTimeout(API_CALL_TIMEOUT)
                        .build();
        return SesV2Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(overrideConfiguration)
                .build();
    }
}
