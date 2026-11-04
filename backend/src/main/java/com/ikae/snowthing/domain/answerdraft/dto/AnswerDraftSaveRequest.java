package com.ikae.snowthing.domain.answerdraft.dto;

import java.util.List;

/**
 * 초안 저장 요청. 여기에 없는 필드(confirmed, approved, publish 등)는 읽지 않고 버린다. 모델이 보낸 승인 표시는 상태에 아무 영향이 없다(D7).
 *
 * @param targetPostPublicId 답변을 달 게시글의 publicId (UUID)
 * @param body 댓글로 게시될 본문. 1~1,000자 (댓글 제한과 같다)
 * @param evidence 인용한 근거. 1~20개, evidenceId 중복 불가, chunkHash 필수
 * @param idempotencyKey 작성자별 유일 키. 8~100자, 영문 대소문자, 숫자, {@code . _ : -}
 */
public record AnswerDraftSaveRequest(
        String targetPostPublicId, String body, List<EvidenceRef> evidence, String idempotencyKey) {

    public record EvidenceRef(String evidenceId, String chunkHash) {}
}
