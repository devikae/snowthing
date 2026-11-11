package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

/** 관리자 댓글 목록 항목. 본문은 200자까지만 준다. 회원 내부 ID는 내보내지 않는다. */
public record AdminCommentItem(
        Long commentId,
        String contentPreview,
        String writerNickname,
        String writerPublicId,
        boolean isAnonymous,
        boolean isDeleted,
        boolean isHeld,
        String postPublicId,
        String postTitle,
        String postCategoryCode,
        LocalDateTime createdAt) {}
