package com.ikae.snowthing.domain.search.service;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository;
import com.ikae.snowthing.domain.search.repository.SearchDocumentJdbcRepository.NewSearchDocument;
import com.ikae.snowthing.domain.search.repository.SearchIndexStateRepository;
import com.ikae.snowthing.domain.search.repository.SearchOutboxRepository;
import com.ikae.snowthing.domain.search.repository.SearchOutboxRepository.ClaimedEvent;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository.CommentRef;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository.CommentSource;
import com.ikae.snowthing.domain.search.repository.SearchSourceRepository.PostSource;

import lombok.extern.slf4j.Slf4j;

/**
 * search_outbox 이벤트를 색인에 반영한다.
 *
 * <ul>
 *   <li>이벤트 내용을 믿지 않고 처리 시점의 현재 원문을 다시 읽는다. 순서가 뒤바뀌거나 같은 이벤트가 두 번 와도 결과는 "현재 원문 상태"로 같다(최소 1회 전달 +
 *       멱등).
 *   <li>이벤트 버전이 마지막 적용 버전보다 작으면 이미 더 새로운 상태가 반영된 것이므로 색인을 건드리지 않고 완료 처리한다.
 *   <li>원문이 없거나 볼 수 없으면 문서를 지우고 tombstone(ID·버전·시각)만 남긴다. 내용은 남기지 않는다.
 *   <li>게시글 이벤트는 그 글의 댓글 문서까지 함께 다시 만든다. 글이 숨김·삭제되면 댓글 문서도 색인에서 빠진다.
 *   <li>이벤트 하나 = 트랜잭션 하나. 문서 교체, 상태 갱신, 이벤트 완료 표시를 한 번에 커밋한다. 중간에 죽으면 모두 롤백되고 lease가 끝난 뒤 다시 처리된다.
 *   <li>READ COMMITTED로 실행한다. 상태 행 잠금을 잡은 뒤 원문을 읽을 때 그 사이 커밋된 변경을 보기 위해서다. REPEATABLE READ는 첫 읽기
 *       시점의 스냅샷을 계속 보므로 잠금을 기다리는 동안 커밋된 변경을 놓칠 수 있다.
 *   <li>같은 게시글에 대한 처리는 게시글 상태 행 잠금으로 직렬화한다. 잠금 순서는 항상 게시글 상태, 그다음 댓글 상태(ID 오름차순)다.
 * </ul>
 */
@Slf4j
@Service
public class SearchOutboxProcessor {

    private static final int MAX_ERROR_CODE_LENGTH = 100;

    private final SearchOutboxRepository outbox;
    private final SearchIndexStateRepository states;
    private final SearchDocumentJdbcRepository documents;
    private final SearchSourceRepository sources;
    private final SearchEmbeddingIndexer embeddings;
    private final Clock clock;
    private final TransactionTemplate tx;

    @Value("${snowthing.search.indexer.batch-size:50}")
    private int batchSize;

    @Value("${snowthing.search.indexer.lease-ms:60000}")
    private long leaseMs;

    @Value("${snowthing.search.indexer.max-attempts:5}")
    private int maxAttempts;

    @Value("${snowthing.search.indexer.retry-base-delay-ms:1000}")
    private long retryBaseDelayMs;

    @Value("${snowthing.search.indexer.retry-max-delay-ms:600000}")
    private long retryMaxDelayMs;

    public SearchOutboxProcessor(
            SearchOutboxRepository outbox,
            SearchIndexStateRepository states,
            SearchDocumentJdbcRepository documents,
            SearchSourceRepository sources,
            SearchEmbeddingIndexer embeddings,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.outbox = outbox;
        this.states = states;
        this.documents = documents;
        this.sources = sources;
        this.embeddings = embeddings;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * 한 배치를 가져와 처리한다. 완료 처리한 건수(오래된 이벤트 포함)를 돌려준다. 의미 검색이 켜져 있으면 끝에 현재 벡터가 없는 구간을 채운다(트랜잭션 밖,
     * {@link SearchEmbeddingIndexer#refreshMissing()}). 벡터 갱신 결과는 반환값에 넣지 않는다.
     */
    public int processBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<ClaimedEvent> events =
                tx.execute(
                        status ->
                                outbox.claim(now, now.plus(Duration.ofMillis(leaseMs)), batchSize));
        int done = 0;
        for (ClaimedEvent event : events) {
            try {
                tx.executeWithoutResult(status -> apply(event));
                done++;
            } catch (RuntimeException failure) {
                handleFailure(event, failure);
            }
        }
        embeddings.refreshMissing(done > 0);
        return done;
    }

    private void apply(ClaimedEvent event) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (event.sourceType() == SearchSourceType.POST) {
            long applied = states.lock(SearchSourceType.POST, event.sourceId(), now);
            if (event.version() >= applied) {
                applyPost(event.sourceId(), Math.max(applied, event.version()), now);
                embeddings.removeOutdatedOfPost(event.sourceId());
            }
        } else {
            Optional<CommentRef> ref = sources.findCommentRef(event.sourceId());
            Optional<Long> postId =
                    ref.map(CommentRef::postId)
                            .or(() -> documents.findPostIdOfComment(event.sourceId()));
            postId.ifPresent(id -> states.lock(SearchSourceType.POST, id, now));
            long applied = states.lock(SearchSourceType.COMMENT, event.sourceId(), now);
            if (event.version() >= applied) {
                long missingVersion = Math.max(applied, event.version());
                applyComment(event.sourceId(), missingVersion, now);
                embeddings.removeOutdatedOfComment(event.sourceId());
            }
        }
        outbox.markDone(event.eventId(), now);
    }

    /**
     * 게시글과 그 글의 댓글 문서를 현재 원문 기준으로 통째로 바꾼다.
     *
     * @param missingVersion 게시글이 하드 삭제되어 현재 버전을 읽을 수 없을 때 tombstone에 남길 버전
     */
    private void applyPost(long postId, long missingVersion, LocalDateTime now) {
        Optional<PostSource> visible = sources.findVisiblePost(postId);
        Map<Long, Long> commentVersions = sources.findCommentVersionsOfPost(postId);
        Set<Long> commentIds = new TreeSet<>(commentVersions.keySet());
        commentIds.addAll(documents.findCommentIdsOfPost(postId));

        documents.deleteByPostId(postId);
        List<NewSearchDocument> rows = new ArrayList<>();
        Map<Long, Integer> visibleCommentDocs = new HashMap<>();
        if (visible.isPresent()) {
            PostSource post = visible.get();
            for (SearchChunk chunk : SearchTextChunker.chunkPost(post.title(), post.content())) {
                rows.add(SearchIndexService.toDocument(post, chunk, now));
            }
            states.markApplied(
                    SearchSourceType.POST, postId, post.contentVersion(), rows.size(), now);
            for (CommentSource comment : sources.findVisibleCommentsOfPost(postId)) {
                List<SearchChunk> chunks = SearchTextChunker.chunkComment(comment.content());
                visibleCommentDocs.put(comment.commentId(), chunks.size());
                for (SearchChunk chunk : chunks) {
                    rows.add(SearchIndexService.toDocument(comment, chunk, now));
                }
            }
        } else {
            long version = sources.findPostVersion(postId).orElse(missingVersion);
            states.markTombstoned(SearchSourceType.POST, postId, version, now);
        }

        for (Long commentId : commentIds) {
            long applied = states.lock(SearchSourceType.COMMENT, commentId, now);
            Long current = commentVersions.get(commentId);
            if (visibleCommentDocs.containsKey(commentId)) {
                states.markApplied(
                        SearchSourceType.COMMENT,
                        commentId,
                        current,
                        visibleCommentDocs.get(commentId),
                        now);
            } else {
                states.markTombstoned(
                        SearchSourceType.COMMENT,
                        commentId,
                        current != null ? current : applied,
                        now);
            }
        }
        documents.insertAll(rows);
    }

    private void applyComment(long commentId, long missingVersion, LocalDateTime now) {
        Optional<CommentSource> visible = sources.findVisibleComment(commentId);
        documents.deleteBySource(SearchSourceType.COMMENT, commentId);
        if (visible.isPresent()) {
            CommentSource comment = visible.get();
            List<NewSearchDocument> rows = new ArrayList<>();
            for (SearchChunk chunk : SearchTextChunker.chunkComment(comment.content())) {
                rows.add(SearchIndexService.toDocument(comment, chunk, now));
            }
            documents.insertAll(rows);
            states.markApplied(
                    SearchSourceType.COMMENT, commentId, comment.version(), rows.size(), now);
        } else {
            long version =
                    sources.findCommentRef(commentId)
                            .map(CommentRef::version)
                            .orElse(missingVersion);
            states.markTombstoned(SearchSourceType.COMMENT, commentId, version, now);
        }
    }

    /**
     * 실패한 트랜잭션은 이미 롤백됐다. 새 트랜잭션에서 재시도 시각을 늦추거나, 최대 시도 횟수에 닿았으면 FAILED로 둔다. 오류 기록에는 예외 종류만 남긴다. 예외
     * 메시지에는 SQL 파라미터(원문 일부)가 들어갈 수 있기 때문이다.
     */
    private void handleFailure(ClaimedEvent event, RuntimeException failure) {
        String errorCode = errorCode(failure);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean permanent = event.attempt() >= maxAttempts;
        try {
            tx.executeWithoutResult(
                    status -> {
                        if (permanent) {
                            outbox.markFailed(event.eventId(), now, errorCode);
                        } else {
                            outbox.scheduleRetry(
                                    event.eventId(),
                                    now.plus(Duration.ofMillis(backoffMs(event.attempt()))),
                                    errorCode);
                        }
                    });
        } catch (RuntimeException recordFailure) {
            // 기록하지 못해도 lease가 끝나면 다시 가져가므로 이벤트는 사라지지 않는다.
            log.warn(
                    "search outbox failure not recorded: eventId={}, error={}",
                    event.eventId(),
                    errorCode(recordFailure));
        }
        log.warn(
                "search outbox event {}: eventId={}, sourceType={}, sourceId={}, attempt={},"
                        + " error={}, sql={}",
                permanent ? "failed" : "deferred",
                event.eventId(),
                event.sourceType(),
                event.sourceId(),
                event.attempt(),
                errorCode,
                sqlState(failure));
    }

    /** 1회 실패 후 기본 지연, 이후 두 배씩 늘리고 최대 지연에서 멈춘다. */
    long backoffMs(int attempt) {
        long multiplier = 1L << Math.min(Math.max(attempt - 1, 0), 20);
        return Math.min(retryMaxDelayMs, retryBaseDelayMs * multiplier);
    }

    /** 원인 SQLException의 SQLState와 벤더 코드만 남긴다. 메시지에는 바인딩 값(원문 일부)이 들어갈 수 있어 남기지 않는다. */
    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                return sql.getSQLState() + "/" + sql.getErrorCode();
            }
        }
        return "-";
    }

    private static String errorCode(Throwable failure) {
        String name = failure.getClass().getSimpleName();
        if (name.isEmpty()) {
            name = failure.getClass().getName();
        }
        return name.length() > MAX_ERROR_CODE_LENGTH
                ? name.substring(0, MAX_ERROR_CODE_LENGTH)
                : name;
    }
}
