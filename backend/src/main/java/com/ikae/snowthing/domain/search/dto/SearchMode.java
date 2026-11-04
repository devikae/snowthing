package com.ikae.snowthing.domain.search.dto;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;
import com.ikae.snowthing.global.error.ErrorCode;
import com.ikae.snowthing.global.exception.CustomException;

/**
 * 근거 검색 방식. 기본은 키워드(1단계 기준선). 하이브리드와 재정렬은 IKA-11 비교 실험용이며 의미 검색이 켜진 서버에서만 된다(꺼져 있으면 SEARCH_002).
 */
public enum SearchMode {
    KEYWORD,
    /** 키워드 상위 K와 벡터 상위 K를 RRF로 결합 */
    HYBRID,
    /** 하이브리드 결합 뒤 재정렬기로 다시 정렬. 재정렬기가 설정돼 있어야 한다 */
    HYBRID_RERANK;

    /** null이나 빈 값은 KEYWORD. 대소문자는 구분하지 않는다. 그 밖의 값은 SEARCH_001. */
    public static SearchMode parse(String mode) {
        if (mode == null || mode.isBlank()) {
            return KEYWORD;
        }
        return switch (mode.strip().toLowerCase(Locale.ROOT)) {
            case "keyword" -> KEYWORD;
            case "hybrid" -> HYBRID;
            case "hybrid_rerank" -> HYBRID_RERANK;
            default -> throw new CustomException(ErrorCode.SEARCH_INVALID_REQUEST);
        };
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
