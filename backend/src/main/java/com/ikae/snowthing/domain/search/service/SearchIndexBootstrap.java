package com.ikae.snowthing.domain.search.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.search.dto.SearchReindexResponse;
import com.ikae.snowthing.domain.search.repository.SearchConsistencyRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 빈 색인 첫 채우기. 운영에는 관리자 계정이 없어 IKA-10 배포 뒤 {@code POST /reindex}를 부를 사람이 없고, 기존 게시글은 search_document에 없다.
 *
 * <p>앱이 뜰 때(ApplicationReadyEvent) 아래를 모두 만족하면 기존 체크포인트 재인덱싱({@link
 * SearchConsistencyService#reindex()})을 DONE이 될 때까지 부른다. 재인덱싱은 outbox에 게시글 이벤트만 넣고, 실제 색인은 평소 처리기가 한다.
 *
 * <ul>
 *   <li>{@code snowthing.search.indexer.enabled=true} (처리기가 없으면 넣어도 반영되지 않는다)
 *   <li>{@code snowthing.search.bootstrap-on-empty=true} (기본)
 *   <li>search_index_state가 비어 있다 (한 번이라도 색인한 적이 있으면 실행하지 않는다)
 *   <li>공개 게시글이 있다
 *   <li>재인덱싱 체크포인트가 없거나 RUNNING이다 (DONE이면 이미 넣었고 처리기가 반영 중이다)
 * </ul>
 *
 * <p>여러 인스턴스: 체크포인트가 없을 때는 {@code INSERT IGNORE}로 FULL 체크포인트 행을 먼저 만든 인스턴스만 시작한다. 이미 RUNNING이면 이어서
 * 부른다. 재인덱싱 배치는 체크포인트 행 {@code FOR UPDATE} 잠금 안에서 last_source_id를 옮기므로 두 인스턴스가 같이 이어서 해도 같은 배치를 두 번 넣지
 * 않는다. 혹시 겹쳐 들어간 이벤트가 있어도 처리기가 멱등이라 결과는 같다. 실패해도 기동을 막지 않고 로그만 남긴다(매일 대조가 메운다).
 */
@Slf4j
@Component
public class SearchIndexBootstrap {

    /** 재인덱싱 한 번은 최대 50배치(25,000건). 이 상한이면 125만 건까지 한 번 기동에 넣는다. 그 이상은 다음 기동이나 대조가 잇는다. */
    static final int MAX_REINDEX_CALLS = 50;

    private final SearchConsistencyService consistencyService;
    private final SearchConsistencyRepository consistency;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Value("${snowthing.search.indexer.enabled:true}")
    private boolean indexerEnabled;

    @Value("${snowthing.search.bootstrap-on-empty:true}")
    private boolean bootstrapOnEmpty;

    public SearchIndexBootstrap(
            SearchConsistencyService consistencyService,
            SearchConsistencyRepository consistency,
            JdbcTemplate jdbc,
            Clock clock) {
        this.consistencyService = consistencyService;
        this.consistency = consistency;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            bootstrapIfEmpty();
        } catch (RuntimeException failure) {
            log.warn("search index bootstrap failed: error={}", failure.getClass().getSimpleName());
        }
    }

    /**
     * @return 재인덱싱을 불렀으면 마지막 응답, 조건이 맞지 않아 건너뛰었으면 null
     */
    public SearchReindexResponse bootstrapIfEmpty() {
        if (!indexerEnabled || !bootstrapOnEmpty) {
            return null;
        }
        Integer states =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM (SELECT 1 FROM search_index_state LIMIT 1) s",
                        Integer.class);
        if (states != null && states > 0) {
            return null;
        }
        if (consistency.findVisiblePostsAfter(0, 1).isEmpty()) {
            return null;
        }
        if (!claimOrResume()) {
            return null;
        }
        SearchReindexResponse response = null;
        for (int call = 0; call < MAX_REINDEX_CALLS; call++) {
            response = consistencyService.reindex();
            if (!"RUNNING".equals(response.status())) {
                break;
            }
        }
        log.info(
                "search index bootstrap enqueued: status={}, enqueued={}",
                response.status(),
                response.enqueuedCount());
        return response;
    }

    /** 체크포인트가 없으면 만들어 시작권을 얻고, RUNNING이면 이어서 하고, DONE이면 건너뛴다. */
    private boolean claimOrResume() {
        LocalDateTime now = LocalDateTime.now(clock);
        int claimed =
                jdbc.update(
                        "INSERT IGNORE INTO search_reindex_checkpoint (job_name, status,"
                                + " last_source_id, enqueued_count, started_at, updated_at,"
                                + " finished_at) VALUES (?, 'RUNNING', 0, 0, ?, ?, NULL)",
                        SearchConsistencyService.REINDEX_JOB,
                        now,
                        now);
        if (claimed > 0) {
            return true;
        }
        List<String> status =
                jdbc.queryForList(
                        "SELECT status FROM search_reindex_checkpoint WHERE job_name = ?",
                        String.class,
                        SearchConsistencyService.REINDEX_JOB);
        return !status.isEmpty() && "RUNNING".equals(status.get(0));
    }
}
