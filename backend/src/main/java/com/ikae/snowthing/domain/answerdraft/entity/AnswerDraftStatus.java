package com.ikae.snowthing.domain.answerdraft.entity;

/**
 * 답변 초안 상태.
 *
 * <pre>
 * DRAFT -> REVIEW_REQUIRED -> APPROVED -> PUBLISHED
 *                 |               |
 *                 +--> REJECTED <-+
 * </pre>
 *
 * DRAFT는 저장 요청을 검증하는 동안의 시작 상태다. 검증을 통과해 저장된 행은 항상 REVIEW_REQUIRED부터 시작한다. APPROVED와 PUBLISHED로 가는
 * 전이는 관리자 세션 요청에서만 일어난다.
 */
public enum AnswerDraftStatus {
    DRAFT,
    REVIEW_REQUIRED,
    APPROVED,
    PUBLISHED,
    REJECTED
}
