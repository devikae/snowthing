package com.ikae.snowthing.domain.search.dto;

import java.util.Locale;
import java.util.regex.Pattern;

import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 근거 검색 입력. 쿼리 파라미터를 문자열로 받아 여기서 검증한다. 숫자 변환 실패나 누락을 Spring 기본 예외로 두면 전역 처리기에서 500이 되기 때문이다.
 *
 * @param query 앞뒤 공백을 제거한 검색어 (2~100자)
 * @param size 결과 수 (1~20)
 * @param categoryCode 현재 게시판 코드 필터. null이면 전체
 */
public record EvidenceSearchCondition(String query, int size, String categoryCode) {

    public static final int MIN_QUERY_LENGTH = 2;
    public static final int MAX_QUERY_LENGTH = 100;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 20;

    private static final Pattern CATEGORY_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,49}$");

    public static EvidenceSearchCondition of(String q, String size, String categoryCode) {
        String query = q == null ? "" : q.strip();
        int queryLength = query.codePointCount(0, query.length());
        if (queryLength < MIN_QUERY_LENGTH || queryLength > MAX_QUERY_LENGTH) {
            throw invalid();
        }
        return new EvidenceSearchCondition(query, parseSize(size), parseCategory(categoryCode));
    }

    private static int parseSize(String size) {
        if (size == null || size.isBlank()) {
            return DEFAULT_SIZE;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(size.strip());
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (parsed < 1 || parsed > MAX_SIZE) {
            throw invalid();
        }
        return parsed;
    }

    private static String parseCategory(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        String code = categoryCode.strip().toUpperCase(Locale.ROOT);
        if (!CATEGORY_CODE.matcher(code).matches()) {
            throw invalid();
        }
        return code;
    }

    private static CustomException invalid() {
        return new CustomException(ErrorCode.SEARCH_INVALID_REQUEST);
    }
}
