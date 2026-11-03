package com.ikae.snowthing.domain.search.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ikae.snowthing.domain.search.entity.SearchSourceType;

/**
 * 근거 검색 결과 한 건. 검색 점수는 신뢰도로 오해되지 않도록 반환하지 않는다(설계 D4). 게시글 내부 ID와 작성자 정보는 넣지 않는다. 댓글 ID는 기존 댓글 API에서
 * 이미 공개하는 값이라 문자열로 반환한다.
 *
 * @param title 현재 게시글 제목 (표시용)
 * @param excerpt 색인된 구간 텍스트 앞부분 (최대 200자)
 */
public record EvidenceSearchItemResponse(
        String evidenceId,
        SearchSourceType sourceType,
        String postPublicId,
        @JsonSerialize(using = ToStringSerializer.class) Long commentId,
        @JsonSerialize(using = ToStringSerializer.class) Long parentCommentId,
        int chunkNo,
        long sourceVersion,
        String chunkHash,
        String url,
        String title,
        String excerpt,
        boolean excerptTruncated,
        LocalDateTime sourceCreatedAt,
        LocalDateTime sourceUpdatedAt,
        LocalDateTime indexedAt) {}
