package com.ikae.snowthing.domain.carpool.external;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CarpoolExternalApiProperties.class)
public class CarpoolExternalApiConfig {}
