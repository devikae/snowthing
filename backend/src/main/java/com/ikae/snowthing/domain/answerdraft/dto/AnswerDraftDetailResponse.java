package com.ikae.snowthing.domain.answerdraft.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftAuditAction;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftStatus;

/**
 * 관리자 검토 화면용 초안 상세. 근거마다 지금 원문을 다시 읽어 저장 시점 해시와 맞는지 함께 보여 준다.
 *
 * @param publishEnabled 게시 기능 플래그
 */
public record AnswerDraftDetailResponse(
        String draftId,
        AnswerDraftStatus status,
        long version,
        Target target,
        String body,
        List<Evidence> evidence,
        String createdByNickname,
        boolean createdByToken,
        LocalDateTime createdAt,
        String reviewedByNickname,
        LocalDateTime reviewedAt,
        String approvedByNickname,
        LocalDateTime approvedAt,
        Long approvedPostContentVersion,
        String rejectReason,
        @JsonSerialize(using = ToStringSerializer.class) Long publishedCommentId,
        String publishedByNickname,
        LocalDateTime publishedAt,
        boolean publishEnabled,
        List<Audit> audits) {

    /**
     * @param visible 지금 공개 범위 안에 있는지
     * @param title 볼 수 있을 때만 현재 제목
     * @param url 볼 수 있을 때만 화면 경로
     * @param contentVersion 볼 수 있을 때만 현재 content_version
     */
    public record Target(
            String postPublicId, boolean visible, String title, String url, Long contentVersion) {}

    /**
     * @param currentStatus MATCH(저장 시점 해시와 같음), CHANGED(원문이 바뀜), UNAVAILABLE(원문이 없거나 볼 수 없음)
     * @param title 볼 수 있을 때만 원문 게시글 제목
     * @param url 볼 수 있을 때만 원문 화면 경로
     * @param text 볼 수 있을 때만 현재 구간 텍스트(최대 1,000자)
     */
    public record Evidence(
            String evidenceId,
            String chunkHash,
            String currentStatus,
            String title,
            String url,
            String text) {}

    public record Audit(
            AnswerDraftAuditAction action,
            String actorNickname,
            boolean viaToken,
            String reasonCode,
            LocalDateTime createdAt) {}
}
