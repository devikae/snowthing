package com.ikae.snowthing.domain.answerdraft.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ikae.snowthing.domain.answerdraft.entity.AnswerDraftStatus;

/**
 * 게시 결과.
 *
 * @param alreadyPublished 이미 게시된 초안을 다시 게시하려 했으면 true. 이때도 처음 만든 댓글을 그대로 돌려준다
 */
public record AnswerDraftPublishResponse(
        String draftId,
        AnswerDraftStatus status,
        @JsonSerialize(using = ToStringSerializer.class) Long publishedCommentId,
        LocalDateTime publishedAt,
        String targetPostPublicId,
        boolean alreadyPublished) {}
