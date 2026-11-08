package com.ikae.snowthing.domain.answerdraft.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftDetailResponse;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftListResponse;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftPublishResponse;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftRejectRequest;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftStatusResponse;
import com.ikae.snowthing.domain.answerdraft.service.AnswerDraftService;
import com.ikae.snowthing.global.security.CustomUserDetails;
import com.ikae.snowthing.global.web.ClientIpResolver;

import lombok.RequiredArgsConstructor;

/**
 * 사람 검토용 초안 API. {@code /api/v1/admin/**}라 ROLE_ADMIN 세션만 들어온다. API 토큰 인증에는 ROLE_ADMIN이 없으므로 어떤 범위의
 * 토큰도 승인하거나 게시할 수 없다.
 */
@RestController
@RequestMapping("/api/v1/admin/answer-drafts")
@RequiredArgsConstructor
public class AnswerDraftAdminController {

    private final AnswerDraftService answerDraftService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    public ResponseEntity<AnswerDraftListResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(answerDraftService.list(status, page, size));
    }

    @GetMapping("/{draftId}")
    public ResponseEntity<AnswerDraftDetailResponse> detail(@PathVariable String draftId) {
        return ResponseEntity.ok(answerDraftService.detail(draftId));
    }

    @PostMapping("/{draftId}/approve")
    public ResponseEntity<AnswerDraftStatusResponse> approve(
            @PathVariable String draftId, @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(answerDraftService.approve(draftId, admin));
    }

    @PostMapping("/{draftId}/reject")
    public ResponseEntity<AnswerDraftStatusResponse> reject(
            @PathVariable String draftId,
            @RequestBody(required = false) AnswerDraftRejectRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        return ResponseEntity.ok(answerDraftService.reject(draftId, request, admin));
    }

    @PostMapping("/{draftId}/publish")
    public ResponseEntity<AnswerDraftPublishResponse> publish(
            @PathVariable String draftId,
            @AuthenticationPrincipal CustomUserDetails admin,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                answerDraftService.publish(draftId, admin, clientIpResolver.resolve(request)));
    }
}
