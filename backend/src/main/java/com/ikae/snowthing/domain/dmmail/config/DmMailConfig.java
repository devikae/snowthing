package com.ikae.snowthing.domain.dmmail.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ikae.snowthing.domain.dmmail.mail.DisabledDmMailSender;
import com.ikae.snowthing.domain.dmmail.mail.DmMailSender;
import com.ikae.snowthing.domain.dmmail.mail.SesDmMailSender;

/**
 * 쪽지 메일 발송기. 메일 공급자가 ses일 때만 실제 SES 클라이언트를 만든다. 인증메일의 {@code SesMailConfig} 빈과 별개의 클라이언트다(재시도 정책이
 * 다르다).
 */
@Configuration
@EnableConfigurationProperties(DmMailProperties.class)
public class DmMailConfig {

    @Bean
    public DmMailSender dmMailSender(
            DmMailProperties properties,
            @Value("${snowthing.mail.provider:disabled}") String provider,
            @Value("${snowthing.mail.region:ap-northeast-2}") String region,
            @Value("${snowthing.mail.from-address:help@snowthing.org}") String fromAddress,
            @Value("${snowthing.mail.from-name:SnowThing}") String fromName,
            @Value("${snowthing.mail.configuration-set:}") String configurationSet) {
        if (!"ses".equals(provider)) {
            return new DisabledDmMailSender();
        }
        return new SesDmMailSender(
                SesDmMailSender.createClient(region, properties.ses(), null),
                fromAddress,
                fromName,
                configurationSet);
    }
}
