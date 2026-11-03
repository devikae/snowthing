package com.ikae.snowthing.domain.search.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.ikae.snowthing.domain.search.dto.SearchOutboxSummaryResponse;
import com.ikae.snowthing.domain.search.dto.SearchReconcileResponse;
import com.ikae.snowthing.domain.search.dto.SearchReindexResponse;
import com.ikae.snowthing.domain.search.entity.SearchSourceType;
import com.ikae.snowthing.domain.search.repository.SearchConsistencyRepository;
import com.ikae.snowthing.domain.search.repository.SearchConsistencyRepository.SourceVersion;
import com.ikae.snowthing.domain.search.repository.SearchOutboxRepository;
import com.ikae.snowthing.domain.search.repository.SearchOutboxRepository.Status;
import com.ikae.snowthing.domain.search.service.SearchOutboxWriter.NewEvent;

import lombok.extern.slf4j.Slf4j;

/**
 * 색인 정합성 관리: outbox 현황·재시도, 대조, 무중단 재인덱싱.
 *
 * <p>대조와 재인덱싱은 색인을 직접 고치지 않고 outbox에 이벤트만 넣는다. 실제 반영은 처리기 한 곳에서만 하므로 잠금·버전 규칙이 한 군데에 모인다.
 */
@Slf4j
@Service
public class SearchConsistencyService {

    static final int PURGE_BATCH_SIZE = 1000;
    static final int PURGE_MAX_ROUNDS = 100;
    static final int REINDEX_MAX_BATCHES_PER_CALL = 50;

    static final String REINDEX_JOB = "FULL";

    private final SearchConsistencyRepository consistency;
    private final SearchOutboxRepository outbox;
    private final SearchOutboxWriter writer;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    @Value("${snowthing.search.reindex.batch-size:500}")
    private int reindexBatchSize;

    @Value("${snowthing.search.outbox.done-retention-days:7}")
    private int doneRetentionDays;

    public SearchConsistencyService(
            SearchConsistencyRepository consistency,
            SearchOutboxRepository outbox,
            SearchOutboxWriter writer,
            JdbcTemplate jdbc,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.consistency = consistency;
        this.outbox = outbox;
        this.writer = writer;
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public SearchOutboxSummaryResponse summary() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Status, Long> counts = outbox.countByStatus();
        LocalDateTime oldest = outbox.findOldestPendingCreatedAt();
        return new SearchOutboxSummaryResponse(
                counts.get(Status.PENDING),
                counts.get(Status.DONE),
                counts.get(Status.FAILED),
                oldest,
                oldest == null ? null : Math.max(0, Duration.between(oldest, now).toSeconds()),
                now);
    }

    public int retryFailed() {
        Integer retried = tx.execute(status -> outbox.retryFailed(LocalDateTime.now(clock)));
        log.info("search outbox failed events reset: count={}", retried);
        return retried == null ? 0 : retried;
    }

    /** 원문과 색인 상태·문서를 비교해 어긋난 원문을 outbox에 넣는다. 원문이 기준이다. 한 트랜잭션에서 읽고 넣는다. 결과는 처리기가 반영한 뒤에 맞춰진다. */
    public SearchReconcileResponse reconcile() {
        SearchReconcileResponse result =
                tx.execute(
                        status -> {
                            List<SourceVersion> postUpserts = consistency.findPostUpsertDrift();
                            List<SourceVersion> postDeletes = consistency.findPostDeleteDrift();
                            List<SourceVersion> commentUpserts =
                                    consistency.findCommentUpsertDrift();
                            List<SourceVersion> commentDeletes =
                                    consistency.findCommentDeleteDrift();
                            enqueue(SearchSourceType.POST, postUpserts, SearchOutboxWriter.UPSERT);
                            enqueue(SearchSourceType.POST, postDeletes, SearchOutboxWriter.DELETE);
                            enqueue(
                                    SearchSourceType.COMMENT,
                                    commentUpserts,
                                    SearchOutboxWriter.UPSERT);
                            enqueue(
                                    SearchSourceType.COMMENT,
                                    commentDeletes,
                                    SearchOutboxWriter.DELETE);
                            return new SearchReconcileResponse(
                                    postUpserts.size(),
                                    postDeletes.size(),
                                    commentUpserts.size(),
                                    commentDeletes.size(),
                                    LocalDateTime.now(clock));
                        });
        log.info(
                "search reconcile enqueued: postUpserts={}, postDeletes={}, commentUpserts={},"
                        + " commentDeletes={}",
                result.postUpserts(),
                result.postDeletes(),
                result.commentUpserts(),
                result.commentDeletes());
        return result;
    }

    /**
     * 공개 게시글 전체를 ID 순으로 나눠 outbox에 넣는다. 게시글 이벤트는 그 글의 댓글까지 다시 만든다.
     *
     * <p>기존 문서를 먼저 지우지 않는다. 처리기가 원문 하나씩 문서를 교체하므로 재인덱싱 중에도 검색은 이전 문서를 계속 본다. 배치마다 체크포인트를 같은 트랜잭션에
     * 커밋하므로, 중간에 멈추면 다음 호출이 마지막 체크포인트부터 이어서 한다. 이미 끝난 작업을 다시 부르면 처음부터 새로 시작한다.
     */
    public SearchReindexResponse reindex() {
        boolean resumed = Boolean.TRUE.equals(tx.execute(status -> startReindex()));
        int batches = 0;
        // 요청 스레드를 오래 잡지 않도록 호출당 배치 수를 제한한다. 남으면 status=RUNNING으로 돌려주고 다시 부르면 이어서 한다.
        while (batches < REINDEX_MAX_BATCHES_PER_CALL
                && Boolean.TRUE.equals(tx.execute(status -> runReindexBatch()))) {
            batches++;
        }
        SearchReindexResponse response = readCheckpoint(resumed, batches);
        log.info(
                "search reindex enqueued: resumed={}, batches={}, enqueued={}",
                resumed,
                batches,
                response.enqueuedCount());
        return response;
    }

    /** RUNNING 체크포인트가 있으면 그대로 두고 true(이어서 하기), 없거나 DONE이면 처음부터 시작한다. */
    Boolean startReindex() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> current =
                jdbc.queryForList(
                        "SELECT status FROM search_reindex_checkpoint WHERE job_name = ? FOR UPDATE",
                        String.class,
                        REINDEX_JOB);
        if (!current.isEmpty() && "RUNNING".equals(current.get(0))) {
            return true;
        }
        jdbc.update(
                "INSERT INTO search_reindex_checkpoint (job_name, status, last_source_id,"
                        + " enqueued_count, started_at, updated_at, finished_at)"
                        + " VALUES (?, 'RUNNING', 0, 0, ?, ?, NULL)"
                        + " ON DUPLICATE KEY UPDATE status = 'RUNNING', last_source_id = 0,"
                        + " enqueued_count = 0, started_at = ?, updated_at = ?, finished_at = NULL",
                REINDEX_JOB,
                now,
                now,
                now,
                now);
        return false;
    }

    /** 배치 하나를 넣고 체크포인트를 옮긴다. 더 넣을 게 남았으면 true. 체크포인트 행 잠금으로 동시 호출이 같은 배치를 두 번 넣지 않게 한다. */
    Boolean runReindexBatch() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> checkpoint =
                jdbc.queryForMap(
                        "SELECT status, last_source_id FROM search_reindex_checkpoint"
                                + " WHERE job_name = ? FOR UPDATE",
                        REINDEX_JOB);
        if (!"RUNNING".equals(checkpoint.get("status"))) {
            return false;
        }
        long lastId = ((Number) checkpoint.get("last_source_id")).longValue();
        List<SourceVersion> posts = consistency.findVisiblePostsAfter(lastId, reindexBatchSize);
        enqueue(SearchSourceType.POST, posts, SearchOutboxWriter.UPSERT);
        boolean more = posts.size() == reindexBatchSize;
        long nextId = posts.isEmpty() ? lastId : posts.get(posts.size() - 1).sourceId();
        jdbc.update(
                "UPDATE search_reindex_checkpoint SET last_source_id = ?,"
                        + " enqueued_count = enqueued_count + ?, updated_at = ?,"
                        + " status = ?, finished_at = ? WHERE job_name = ?",
                nextId,
                posts.size(),
                now,
                more ? "RUNNING" : "DONE",
                more ? null : now,
                REINDEX_JOB);
        return more;
    }

    private SearchReindexResponse readCheckpoint(boolean resumed, int batches) {
        return jdbc.queryForObject(
                "SELECT status, last_source_id, enqueued_count, started_at, finished_at"
                        + " FROM search_reindex_checkpoint WHERE job_name = ?",
                (rs, rowNum) ->
                        new SearchReindexResponse(
                                rs.getString("status"),
                                resumed,
                                batches,
                                rs.getLong("last_source_id"),
                                rs.getLong("enqueued_count"),
                                rs.getObject("started_at", LocalDateTime.class),
                                rs.getObject("finished_at", LocalDateTime.class)),
                REINDEX_JOB);
    }

    /**
     * 보관 기간이 지난 DONE 이벤트를 1,000행씩 나눠 지운다. 한 번에 지우면 잠금이 길어지고, 한 번만 지우면 재인덱싱처럼 하루 생성량이 많을 때 정리가 따라가지
     * 못한다. 라운드 상한으로 무한 반복을 막는다.
     */
    public int purgeDoneEvents() {
        LocalDateTime before = LocalDateTime.now(clock).minusDays(doneRetentionDays);
        int total = 0;
        for (int round = 0; round < PURGE_MAX_ROUNDS; round++) {
            Integer purged = tx.execute(status -> outbox.purgeDone(before, PURGE_BATCH_SIZE));
            int count = purged == null ? 0 : purged;
            total += count;
            if (count < PURGE_BATCH_SIZE) {
                break;
            }
        }
        return total;
    }

    private void enqueue(SearchSourceType type, List<SourceVersion> sources, String operation) {
        if (sources.isEmpty()) {
            return;
        }
        writer.enqueueAll(
                sources.stream()
                        .map(s -> new NewEvent(type, s.sourceId(), s.version(), operation))
                        .toList());
    }
}
