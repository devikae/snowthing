package com.ikae.snowthing.global.common.dto;

import java.util.List;

/**
 * 웹 Offset 및 모바일 Keyset Cursor 공통 Wrapper 응답 DTO
 */
public record CursorPageResponse<T>(
        List<T> content,
        PageInfo pageInfo
) {
    public record PageInfo(
            Integer page,           // 웹용: 현재 페이지 번호 (모바일 시 null)
            Integer totalPages,     // 웹용: 전체 페이지 수 (모바일 시 null)
            Long totalElements,     // 웹용: 전체 레코드 수 (모바일 시 null)
            String nextCursor,      // 모바일용: 다음 커서 토큰 (웹 시 null)
            Boolean hasNext,        // 공통: 다음 데이터 존재 여부
            Integer pageSize        // 공통: 한 페이지 크기
    ) {
        // 웹 Offset용 팩토리 메서드
        public static PageInfo ofOffset(int page, int totalPages, long totalElements, boolean hasNext, int pageSize) {
            return new PageInfo(page, totalPages, totalElements, null, hasNext, pageSize);
        }

        // 모바일 Cursor용 팩토리 메서드
        public static PageInfo ofCursor(String nextCursor, boolean hasNext, int pageSize) {
            return new PageInfo(null, null, null, nextCursor, hasNext, pageSize);
        }
    }
}
