package com.ikae.snowthing.domain.search.semantic;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 임베딩 제공자 선택. {@code enabled=true}이고 {@code provider}가 지정됐을 때만 제공자 빈을 만든다. 꺼진 상태에서 provider만 미리 적어
 * 두면 HTTP 제공자의 설정 검사(키, 주소 누락)가 기동을 막을 수 있어 둘 다 조건으로 건다.
 */
@Configuration
@EnableConfigurationProperties(SemanticSearchProperties.class)
public class SemanticSearchConfig {

    /** 로컬 파이프라인 확인용. 외부 호출과 비용이 없고 의미 모델이 아니다. */
    @Bean
    @ConditionalOnExpression(
            "${snowthing.search.semantic.enabled:false}"
                    + " and '${snowthing.search.semantic.provider:none}' == 'hashing'")
    public EmbeddingProvider hashingEmbeddingProvider() {
        return new HashingEmbeddingProvider();
    }

    /** OpenAI 호환 엔드포인트. 호출마다 비용이 생길 수 있어 켜기 전에 사용자 승인이 필요하다. */
    @Bean
    @ConditionalOnExpression(
            "${snowthing.search.semantic.enabled:false}"
                    + " and '${snowthing.search.semantic.provider:none}' == 'http'")
    public EmbeddingProvider httpEmbeddingProvider(SemanticSearchProperties properties) {
        return new HttpEmbeddingProvider(properties.httpOrEmpty());
    }
}
