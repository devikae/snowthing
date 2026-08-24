package com.ikae.snowthing.domain.post.dto;

public enum SearchType {
    TITLE,          // 제목 검색
    WRITER,         // 작성자 닉네임 검색
    CONTENT,        // 본문 검색
    TITLE_CONTENT   // 제목 + 본문 검색
}
