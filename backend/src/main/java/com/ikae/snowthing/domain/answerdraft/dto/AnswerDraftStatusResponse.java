package com.ikae.snowthing.domain.answerdraft.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraft;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftStatus;

/**
 * 초안 상태. MCP {@code get_job_status}가 이 응답을 그대로 쓴다. 본문과 근거 원문은 담지 않는다.
 *
 * @param targetPostUrl 대상 게시글 화면 경로. 지금 볼 수 없는 게시글이면 null
 * @param reviewUrl 사람이 검토할 관리자 화면 경로
 * @param publishedCommentId 게시되었으면 댓글 ID(문자열), 아니면 null
 */
public record AnswerDraftStatusResponse(
        String draftId,
        AnswerDraftStatus status,
        String targetPostPublicId,
        String targetPostUrl,
        int evidenceCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime reviewedAt,
        LocalDateTime approvedAt,
        LocalDateTime publishedAt,
        @JsonSerialize(using = ToStringSerializer.class) Long publishedCommentId,
        String rejectReason,
        String reviewUrl) {

    public static AnswerDraftStatusResponse from(AnswerDraft draft, String targetPostUrl) {
        return new AnswerDraftStatusResponse(
                draft.getPublicId(),
                draft.getStatus(),
                draft.getTargetPostPublicId(),
                targetPostUrl,
                draft.getEvidences().size(),
                draft.getCreatedAt(),
                draft.getUpdatedAt(),
                draft.getReviewedAt(),
                draft.getApprovedAt(),
                draft.getPublishedAt(),
                draft.getPublishedCommentId(),
                draft.getRejectReason(),
                reviewUrl(draft.getPublicId()));
    }

    public static String reviewUrl(String draftPublicId) {
        return "/admin/answer-drafts/" + draftPublicId;
    }
}
