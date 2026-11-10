package com.ikae.snowthing.domain.admin.service;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/** 관리자 목록 쪽 번호와 크기 검사. 크기는 최대 50. */
record AdminPaging(int page, int size) {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    static final int MAX_KEYWORD_LENGTH = 100;

    static AdminPaging of(Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNumber < 0 || pageNumber > 10_000 || pageSize < 1 || pageSize > MAX_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return new AdminPaging(pageNumber, pageSize);
    }

    int offset() {
        return page * size;
    }

    /** 다음 쪽 여부를 알려고 한 건 더 읽는다. */
    int fetchLimit() {
        return size + 1;
    }

    /** 앞뒤 공백을 지운 검색어. 비었으면 null, 너무 길면 400. LIKE 특수문자는 글자로 찾게 이스케이프한다. */
    static String containsPattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String stripped = keyword.strip();
        if (stripped.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return "%" + stripped.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
