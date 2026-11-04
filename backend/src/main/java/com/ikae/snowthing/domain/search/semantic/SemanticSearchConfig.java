package com.ikae.snowthing.domain.search.semantic;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 임베딩 제공자 선택. {@code provider}가 {@code none}(기본)이면 제공자 빈이 없다. 제공자 빈이 있어도 {@code enabled=false}면 {@link
 * SemanticSearchSupport}가 부르지 않는다.
 */
@Configuration
@EnableConfigurationProperties(SemanticSearchProperties.class)
public class SemanticSearchConfig {

    /** 로컬 파이프라인 확인용. 외부 호출과 비용이 없고 의미 모델이 아니다. */
    @Bean
    @ConditionalOnProperty(name = "snowthing.search.semantic.provider", havingValue = "hashing")
    public EmbeddingProvider hashingEmbeddingProvider() {
        return new HashingEmbeddingProvider();
    }

    /** OpenAI 호환 엔드포인트. 호출마다 비용이 생길 수 있어 켜기 전에 사용자 승인이 필요하다. */
    @Bean
    @ConditionalOnProperty(name = "snowthing.search.semantic.provider", havingValue = "http")
    public EmbeddingProvider httpEmbeddingProvider(SemanticSearchProperties properties) {
        return new HttpEmbeddingProvider(properties.httpOrEmpty());
    }
}
