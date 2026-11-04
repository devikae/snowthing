package com.ikae.snowthing.domain.search.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.domain.search.repository.SearchEmbeddingJdbcRepository;
import com.ikae.snowthing.domain.search.repository.SearchEmbeddingJdbcRepository.MissingEmbedding;
import com.ikae.snowthing.domain.search.semantic.EmbeddingProvider;
import com.ikae.snowthing.domain.search.semantic.EmbeddingResult;
import com.ikae.snowthing.domain.search.semantic.SemanticSearchSupport;
import com.ikae.snowthing.domain.search.semantic.VectorMath;

import lombok.extern.slf4j.Slf4j;

/**
 * 구간 벡터 갱신 (IKA-11). outbox 처리기에 두 지점으로 붙는다.
 *
 * <ol>
 *   <li>이벤트 처리 트랜잭션 안: {@link #removeOutdatedOfPost}와 {@link #removeOutdatedOfComment}가 현재 구간과 맞지 않는 벡터(삭제,
 *       비공개, 내용 변경)를 문서 교체와 함께 지운다. tombstone이 되면 벡터도 같은 커밋에서 사라진다. 원문에서 나온 파생 데이터라 의미 검색이 꺼져 있어도
 *       지운다(DB 문장만 있고 제공자 호출은 없다).
 *   <li>배치 처리 뒤, 트랜잭션 밖: {@link #refreshMissing()}이 현재 벡터가 없는 구간을 제공자로 보내고, 그 구간이 아직 같은 해시일 때만 저장한다.
 *       외부 호출을 행 잠금을 잡은 트랜잭션 안에서 하지 않기 위해 분리했다. 의미 검색이 꺼져 있으면 아무 쿼리도, 제공자 호출도 하지 않는다.
 * </ol>
 *
 * <p>오래된 이벤트는 처리기가 문서를 건드리지 않고 끝내므로 벡터도 그대로다. 제공자가 실패하면 문서 색인은 그대로 두고, 지수 백오프 동안 갱신을 쉰다.
 */
@Slf4j
@Service
public class SearchEmbeddingIndexer {

    private static final long PAUSE_BASE_MS = 5_000;
    private static final long PAUSE_MAX_MS = 600_000;

    private final SearchEmbeddingJdbcRepository embeddings;
    private final SemanticSearchSupport semantic;
    private final Clock clock;

    private int consecutiveFailures;
    private LocalDateTime pausedUntil;

    public SearchEmbeddingIndexer(
            SearchEmbeddingJdbcRepository embeddings, SemanticSearchSupport semantic, Clock clock) {
        this.embeddings = embeddings;
        this.semantic = semantic;
        this.clock = clock;
    }

    public void removeOutdatedOfPost(long postId) {
        embeddings.deleteOutdatedOfPost(postId);
    }

    public void removeOutdatedOfComment(long commentId) {
        embeddings.deleteOutdatedOfSource(SearchSourceType.COMMENT, commentId);
    }

    /**
     * 현재 벡터가 없는 구간을 배치로 채운다.
     *
     * @return 저장한 벡터 수
     */
    public synchronized int refreshMissing() {
        if (!semantic.enabled()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (pausedUntil != null && now.isBefore(pausedUntil)) {
            return 0;
        }
        EmbeddingProvider provider = semantic.provider();
        int saved = 0;
        try {
            for (int batch = 0; batch < semantic.properties().effectiveRefreshMaxBatches(); batch++) {
                List<MissingEmbedding> missing =
                        embeddings.findMissing(
                                provider.modelId(),
                                provider.dimension(),
                                semantic.properties().effectiveRefreshBatchSize());
                if (missing.isEmpty()) {
                    break;
                }
                saved += embedAndSave(provider, missing);
            }
            consecutiveFailures = 0;
            pausedUntil = null;
        } catch (RuntimeException failure) {
            consecutiveFailures++;
            long pauseMs =
                    Math.min(
                            PAUSE_MAX_MS,
                            PAUSE_BASE_MS << Math.min(consecutiveFailures - 1, 10));
            pausedUntil = now.plus(Duration.ofMillis(pauseMs));
            // 예외 메시지에는 원문이나 응답 본문이 섞일 수 있어 종류만 남긴다.
            log.warn(
                    "search embedding refresh paused: error={}, failures={}, pauseMs={}",
                    failure.getClass().getSimpleName(),
                    consecutiveFailures,
                    pauseMs);
        }
        return saved;
    }

    private int embedAndSave(EmbeddingProvider provider, List<MissingEmbedding> missing) {
        List<MissingEmbedding> nonBlank = new ArrayList<>();
        for (MissingEmbedding item : missing) {
            if (item.text() != null && !item.text().isBlank()) {
                nonBlank.add(item);
            }
        }
        List<float[]> vectors = List.of();
        if (!nonBlank.isEmpty()) {
            EmbeddingResult result =
                    provider.embed(nonBlank.stream().map(MissingEmbedding::text).toList());
            vectors = result.vectors();
            if (vectors.size() != nonBlank.size()) {
                throw new IllegalStateException("embedding count mismatch");
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        int saved = 0;
        int next = 0;
        for (MissingEmbedding item : missing) {
            // 빈 구간은 제공자에 보내지 않고 0 벡터로 둔다. 다시 "없음"으로 잡혀 계속 호출되는 일을 막는다.
            float[] vector =
                    nonBlank.contains(item) ? vectors.get(next++) : new float[provider.dimension()];
            if (vector.length != provider.dimension() || !VectorMath.isFinite(vector)) {
                throw new IllegalStateException("embedding vector is invalid");
            }
            if (embeddings.saveIfCurrent(
                    item.evidenceId(),
                    item.contentHash(),
                    provider.modelId(),
                    provider.dimension(),
                    VectorMath.toBytes(vector),
                    now)) {
                saved++;
            }
        }
        return saved;
    }
}
