package com.ikae.snowthing.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ikae.snowthing.global.interceptor.VisitorTrackingInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final VisitorTrackingInterceptor visitorTrackingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(visitorTrackingInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/ws-chat/**",
                        "/actuator/**",
                        "/error",
                        "/favicon.ico",
                        "/static/**",
                        "/css/**",
                        "/js/**",
                        "/images/**");
    }
}
