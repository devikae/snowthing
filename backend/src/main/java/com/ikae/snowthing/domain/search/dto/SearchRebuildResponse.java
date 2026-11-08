package com.ikae.snowthing.domain.search.dto;

import java.time.LocalDateTime;

/**
 * 전체 재구축 결과.
 *
 * @param postCount 색인한 공개 게시글 수
 * @param postChunkCount 게시글 구간 수 (제목 구간 포함)
 * @param commentCount 색인한 댓글 수
 * @param documentCount search_document에 넣은 행 수 (postChunkCount + commentCount)
 * @param removedCount 재구축 전에 지운 행 수
 */
public record SearchRebuildResponse(
        int postCount,
        int postChunkCount,
        int commentCount,
        int documentCount,
        int removedCount,
        LocalDateTime indexedAt) {}
