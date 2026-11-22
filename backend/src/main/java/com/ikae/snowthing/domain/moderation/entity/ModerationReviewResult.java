package com.ikae.snowthing.domain.moderation.entity;

/**
 * 관리자 처리 결과. 문자열로 저장하므로 이름을 바꾸지 않는다.
 *
 * <ul>
 *   <li>PASS: 문제없음. 숨김을 풀고 같은 내용 해시는 다시 숨기지 않는다.
 *   <li>KEEP: 숨김 유지. 검토 대기 항목이면 이때 숨긴다.
 *   <li>DELETE: 삭제.
 *   <li>SUPERSEDED: 작성자가 고쳐 새 판정이 생겨 이 판정은 검토할 필요가 없어졌다(시스템 처리).
 * </ul>
 */
public enum ModerationReviewResult {
    PASS,
    KEEP,
    DELETE,
    SUPERSEDED
}
