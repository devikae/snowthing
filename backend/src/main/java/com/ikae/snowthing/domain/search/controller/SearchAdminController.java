package com.ikae.snowthing.domain.search.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.search.dto.EvidenceDetailResponse;
import com.ikae.snowthing.domain.search.dto.EvidenceSearchCondition;
import com.ikae.snowthing.domain.search.dto.EvidenceSearchResponse;
import com.ikae.snowthing.domain.search.dto.SearchOutboxSummaryResponse;
import com.ikae.snowthing.domain.search.dto.SearchRebuildResponse;
import com.ikae.snowthing.domain.search.dto.SearchReconcileResponse;
import com.ikae.snowthing.domain.search.dto.SearchMode;
import com.ikae.snowthing.domain.search.dto.SearchReindexResponse;
import com.ikae.snowthing.domain.search.dto.SearchRetryFailedResponse;
import com.ikae.snowthing.domain.search.service.EvidenceService;
import com.ikae.snowthing.domain.search.service.SearchConsistencyService;
import com.ikae.snowthing.domain.search.service.SearchIndexService;

import lombok.RequiredArgsConstructor;

/**
 * 근거 검색 1단계 API. {@code /api/v1/admin/**}는 SecurityConfig에서 ROLE_ADMIN만 허용하고, 라이브톡 위임 토큰 필터는 관리자
 * 경로를 거부한다.
 */
@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
public class SearchAdminController {

    private final SearchIndexService searchIndexService;
    private final EvidenceService evidenceService;
    private final SearchConsistencyService consistencyService;

    /**
     * 1단계 전체 재구축. 2단계부터는 {@link #reindex()}를 쓴다. 문서를 모두 지우고 다시 넣는 동안 색인 테이블을 넓게 잠그고, 색인 상태
     * (search_index_state)를 갱신하지 않으므로 다음 대조에서 어긋난 원문이 다시 처리된다. 로컬 확인·비상용으로만 남긴다.
     *
     * @deprecated IKA-10. {@code POST /api/v1/admin/search/reindex}로 대체
     */
    @Deprecated
    @PostMapping("/rebuild")
    public ResponseEntity<SearchRebuildResponse> rebuild() {
        return ResponseEntity.ok(searchIndexService.rebuild());
    }

    /** 무중단 재인덱싱. 공개 게시글을 ID 순 배치로 outbox에 넣는다. 멈췄던 작업이 있으면 체크포인트부터 이어서 한다. */
    @PostMapping("/reindex")
    public ResponseEntity<SearchReindexResponse> reindex() {
        return ResponseEntity.ok(consistencyService.reindex());
    }

    /** 원문과 색인을 대조해 어긋난 원문을 outbox에 넣는다. */
    @PostMapping("/reconcile")
    public ResponseEntity<SearchReconcileResponse> reconcile() {
        return ResponseEntity.ok(consistencyService.reconcile());
    }

    @GetMapping("/outbox/summary")
    public ResponseEntity<SearchOutboxSummaryResponse> outboxSummary() {
        return ResponseEntity.ok(consistencyService.summary());
    }

    @PostMapping("/outbox/retry-failed")
    public ResponseEntity<SearchRetryFailedResponse> retryFailed() {
        return ResponseEntity.ok(new SearchRetryFailedResponse(consistencyService.retryFailed()));
    }

    /**
     * 근거 검색. {@code mode}는 선택이며 기본 {@code keyword}(1단계와 같음). {@code hybrid}, {@code hybrid_rerank}는 IKA-11
     * 비교 실험용이고 의미 검색이 꺼진 서버에서는 409 SEARCH_002다.
     */
    @GetMapping("/evidence")
    public ResponseEntity<EvidenceSearchResponse> searchEvidence(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String createdBefore,
            @RequestParam(required = false) String excludePostPublicId,
            @RequestParam(required = false) String mode) {
        SearchMode searchMode = SearchMode.parse(mode);
        return ResponseEntity.ok(
                evidenceService.search(
                        EvidenceSearchCondition.of(
                                q, size, categoryCode, createdBefore, excludePostPublicId),
                        searchMode));
    }

    @GetMapping("/evidence/{evidenceId}")
    public ResponseEntity<EvidenceDetailResponse> getEvidence(
            @PathVariable String evidenceId, @RequestParam(required = false) String expectedHash) {
        return ResponseEntity.ok(evidenceService.getEvidence(evidenceId, expectedHash));
    }
}
