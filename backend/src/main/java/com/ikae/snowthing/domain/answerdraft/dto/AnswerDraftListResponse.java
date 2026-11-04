package com.ikae.snowthing.domain.answerdraft.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftStatus;

/**
 * 관리자 초안 목록. 생성 시각 내림차순, 같으면 ID 내림차순.
 *
 * @param publishEnabled 게시 기능 플래그. 꺼져 있으면 화면이 게시 버튼을 비활성으로 보여 준다
 */
public record AnswerDraftListResponse(
        List<Item> items, int page, int size, boolean hasNext, boolean publishEnabled) {

    /** @param bodyPreview 본문 앞부분 최대 120자 */
    public record Item(
            String draftId,
            AnswerDraftStatus status,
            String targetPostPublicId,
            String bodyPreview,
            int evidenceCount,
            String createdByNickname,
            boolean createdByToken,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {}
}
