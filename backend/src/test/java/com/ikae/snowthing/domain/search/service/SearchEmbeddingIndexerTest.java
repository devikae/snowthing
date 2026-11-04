package com.ikae.snowthing.domain.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.search.repository.SearchEmbeddingJdbcRepository;
import com.ikae.snowthing.domain.search.repository.SearchEmbeddingJdbcRepository.MissingEmbedding;
import com.ikae.snowthing.domain.search.semantic.EmbeddingProvider;
import com.ikae.snowthing.domain.search.semantic.EmbeddingProviderException;
import com.ikae.snowthing.domain.search.semantic.EmbeddingResult;
import com.ikae.snowthing.domain.search.semantic.SemanticSearchProperties;
import com.ikae.snowthing.domain.search.semantic.SemanticSearchSupport;

/** 벡터 채우기의 유휴 간격 제한과 실패 뒤 쉬기. DB와 제공자는 목으로 둔다. */
class SearchEmbeddingIndexerTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T03:00:00Z"));
    private SearchEmbeddingJdbcRepository repository;
    private EmbeddingProvider provider;
    private SearchEmbeddingIndexer indexer;

    @BeforeEach
    void setUp() {
        repository = mock(SearchEmbeddingJdbcRepository.class);
        provider = mock(EmbeddingProvider.class);
        when(provider.modelId()).thenReturn("m");
        when(provider.dimension()).thenReturn(2);
        SemanticSearchSupport semantic = mock(SemanticSearchSupport.class);
        when(semantic.enabled()).thenReturn(true);
        when(semantic.provider()).thenReturn(provider);
        when(semantic.properties())
                .thenReturn(
                        new SemanticSearchProperties(true, "test", 0, 0, 2, 2, 30_000, 0, null));
        when(repository.findMissing(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        indexer = new SearchEmbeddingIndexer(repository, semantic, clock);
    }

    @Test
    @DisplayName("이벤트가 없는 폴링은 30초에 한 번만 조회하고, 이벤트를 처리한 폴링은 바로 조회한다")
    void idlePollsAreGated() {
        indexer.refreshMissing(false);
        indexer.refreshMissing(false);
        clock.advance(Duration.ofSeconds(29));
        indexer.refreshMissing(false);
        verify(repository, times(1)).findMissing(anyString(), anyInt(), anyInt());

        indexer.refreshMissing(true);
        verify(repository, times(2)).findMissing(anyString(), anyInt(), anyInt());

        clock.advance(Duration.ofSeconds(31));
        indexer.refreshMissing(false);
        verify(repository, times(3)).findMissing(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("배치 상한까지 채우고도 남았으면 다음 폴링이 간격 없이 이어서 한다")
    void continuesWhenWorkRemains() {
        MissingEmbedding item = new MissingEmbedding("P:1:1", "a".repeat(64), "본문");
        when(repository.findMissing(anyString(), anyInt(), anyInt())).thenReturn(List.of(item));
        when(provider.embed(List.of("본문")))
                .thenReturn(new EmbeddingResult(List.of(new float[] {1, 0}), null));

        indexer.refreshMissing(false);
        indexer.refreshMissing(false);

        // 상한 2배치 x 2번 호출
        verify(repository, times(4)).findMissing(anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("제공자 실패 뒤에는 이벤트를 처리한 폴링이어도 쉬는 시간을 지킨다")
    void failurePauseWinsOverIndexChange() {
        MissingEmbedding item = new MissingEmbedding("P:1:1", "a".repeat(64), "본문");
        when(repository.findMissing(anyString(), anyInt(), anyInt())).thenReturn(List.of(item));
        when(provider.embed(List.of("본문"))).thenThrow(new EmbeddingProviderException("x"));

        assertThat(indexer.refreshMissing(true)).isZero();
        indexer.refreshMissing(true);
        verify(provider, times(1)).embed(List.of("본문"));

        clock.advance(Duration.ofSeconds(6));
        indexer.refreshMissing(false);
        verify(provider, times(2)).embed(List.of("본문"));
        verify(repository, never())
                .saveIfCurrent(anyString(), anyString(), anyString(), anyInt(), any(), any());
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
