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
import com.ikae.snowthing.domain.search.dto.SearchRebuildResponse;
import com.ikae.snowthing.domain.search.service.EvidenceService;
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

    @PostMapping("/rebuild")
    public ResponseEntity<SearchRebuildResponse> rebuild() {
        return ResponseEntity.ok(searchIndexService.rebuild());
    }

    @GetMapping("/evidence")
    public ResponseEntity<EvidenceSearchResponse> searchEvidence(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String categoryCode) {
        return ResponseEntity.ok(
                evidenceService.search(EvidenceSearchCondition.of(q, size, categoryCode)));
    }

    @GetMapping("/evidence/{evidenceId}")
    public ResponseEntity<EvidenceDetailResponse> getEvidence(
            @PathVariable String evidenceId, @RequestParam(required = false) String expectedHash) {
        return ResponseEntity.ok(evidenceService.getEvidence(evidenceId, expectedHash));
    }
}
