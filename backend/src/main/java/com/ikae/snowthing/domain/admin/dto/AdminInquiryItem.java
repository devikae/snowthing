package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

/** 1:1 문의 목록 항목. 답변 여부는 관리자 회원의 삭제되지 않은 댓글이 있는지로 정한다 (021 설계와 같다). */
public record AdminInquiryItem(
        String publicId,
        String title,
        String writerNickname,
        boolean answered,
        int commentCount,
        LocalDateTime createdAt) {}
