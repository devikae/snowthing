package com.ikae.snowthing.domain.search.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ikae.snowthing.domain.search.entity.SearchSourceType;

/**
 * 원문을 다시 읽어 만든 근거 한 건.
 *
 * @param sourceVersion 현재 원문 버전 (post.content_version 또는 comment.version)
 * @param chunkHash 현재 원문으로 다시 나눈 구간의 해시
 * @param indexedChunkHash 색인에 저장된 해시. 색인에 없으면 null
 * @param stale 색인 해시와 현재 해시가 다르거나 색인에 없으면 true
 * @param text 현재 구간 텍스트 (최대 1,000자)
 * @param indexedAt 색인 시각. 색인에 없으면 null
 */
public record EvidenceDetailResponse(
        String evidenceId,
        SearchSourceType sourceType,
        String postPublicId,
        @JsonSerialize(using = ToStringSerializer.class) Long commentId,
        @JsonSerialize(using = ToStringSerializer.class) Long parentCommentId,
        int chunkNo,
        long sourceVersion,
        String chunkHash,
        String indexedChunkHash,
        boolean stale,
        String url,
        String title,
        String text,
        boolean textTruncated,
        LocalDateTime sourceCreatedAt,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime indexedAt) {}
