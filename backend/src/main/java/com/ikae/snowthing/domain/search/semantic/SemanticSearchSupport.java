package com.ikae.snowthing.domain.search.semantic;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

import lombok.extern.slf4j.Slf4j;

/**
 * 의미 검색 켜짐 여부와 제공자를 한 곳에서 판단한다. 다른 코드는 제공자를 직접 주입받지 않고 이 클래스를 거친다. 꺼져 있으면 {@link #provider()}가
 * SEARCH_002를 던지므로 제공자 호출 경로가 생기지 않는다.
 */
@Slf4j
@Component
public class SemanticSearchSupport {

    private final SemanticSearchProperties properties;
    private final EmbeddingProvider provider;
    private final EvidenceReranker reranker;

    public SemanticSearchSupport(
            SemanticSearchProperties properties,
            ObjectProvider<EmbeddingProvider> providers,
            ObjectProvider<EvidenceReranker> rerankers) {
        this.properties = properties;
        this.provider = properties.enabled() ? providers.getIfAvailable() : null;
        this.reranker = properties.enabled() ? rerankers.getIfAvailable() : null;
        if (properties.enabled() && provider == null) {
            throw new IllegalStateException(
                    "snowthing.search.semantic.enabled=true requires"
                            + " snowthing.search.semantic.provider (hashing or http)");
        }
        if (provider != null) {
            log.info(
                    "semantic search enabled: model={}, rrfK={}, candidateK={}, reranker={}",
                    provider.modelId(),
                    properties.effectiveRrfK(),
                    properties.effectiveCandidateK(),
                    reranker != null ? reranker.modelId() : "none");
        }
    }

    public boolean enabled() {
        return provider != null;
    }

    /** 꺼져 있으면 SEARCH_002. */
    public EmbeddingProvider provider() {
        if (provider == null) {
            throw new CustomException(ErrorCode.SEARCH_MODE_UNAVAILABLE);
        }
        return provider;
    }

    public Optional<EvidenceReranker> reranker() {
        return Optional.ofNullable(reranker);
    }

    public SemanticSearchProperties properties() {
        return properties;
    }
}
