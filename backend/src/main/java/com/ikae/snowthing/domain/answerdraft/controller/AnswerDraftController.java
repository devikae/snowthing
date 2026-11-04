package com.ikae.snowthing.domain.answerdraft.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftSaveRequest;
import com.ikae.snowthing.domain.answerdraft.dto.AnswerDraftStatusResponse;
import com.ikae.snowthing.domain.answerdraft.service.AnswerDraftService;
import com.ikae.snowthing.domain.answerdraft.service.AnswerDraftService.SaveResult;
import com.ikae.snowthing.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/**
 * 초안 저장과 상태 조회. DRAFT 범위 API 토큰 또는 관리자 세션이 쓴다(SecurityConfig). 승인과 게시는 이 컨트롤러에 없다.
 *
 * <p>계약: {@code docs/conception/017_ai_answer_assist/06_answer_draft_flow.md}
 */
@RestController
@RequestMapping("/api/v1/answer-drafts")
@RequiredArgsConstructor
public class AnswerDraftController {

    private final AnswerDraftService answerDraftService;

    /** 새로 저장하면 201, 같은 멱등성 키의 기존 초안을 돌려주면 200. 어느 쪽이든 상태는 사람 검토 대기(REVIEW_REQUIRED) 이후다. */
    @PostMapping
    public ResponseEntity<AnswerDraftStatusResponse> save(
            @RequestBody(required = false) AnswerDraftSaveRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        SaveResult result = answerDraftService.save(request, principal);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(result.draft());
    }

    @GetMapping("/{draftId}")
    public ResponseEntity<AnswerDraftStatusResponse> status(
            @PathVariable String draftId, @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(answerDraftService.status(draftId, principal));
    }
}
