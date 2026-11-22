package com.ikae.snowthing.domain.moderation.entity;

/**
 * 처리 상태. 문자열로 저장하므로 이름을 바꾸지 않는다.
 *
 * <ul>
 *   <li>PENDING: 처리 전이거나 재시도를 기다린다.
 *   <li>DONE: 판정을 마쳤다.
 *   <li>FAILED: 정해진 횟수만큼 실패해 검토 대기(REVIEW)로 넘겼다.
 *   <li>SKIPPED: 처리할 때 대상이 지워졌거나 이미 가려졌거나, 같은 대상의 더 새 수정본이 있어 판정하지 않았다.
 * </ul>
 */
public enum ModerationStatus {
    PENDING,
    DONE,
    FAILED,
    SKIPPED
}
