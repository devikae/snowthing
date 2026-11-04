package com.ikae.snowthing.domain.post.dto;

public record PostSearchRequest(
        String categoryCode, // 카테고리 코드 (FREE, ANONYMOUS, QNA, FOOD, BEST)
        Long resortId, // 리조트 ID (null: 전체, 1: 휘닉스파크 등)
        SearchType searchType, // TITLE, WRITER, CONTENT, TITLE_CONTENT
        String keyword, // 검색 키워드
        PostViewType viewType, // DEFAULT, BEST
        Integer page, // 웹용 페이지 번호 (1-based)
        String cursor, // 모바일용 커서 토큰 (Base64)
        Integer size, // 한 번에 조회할 개수 (기본 20개)
        String writerPublicId // 작성자 조건 (1:1 문의 본인 글 조회용, null: 조건 없음)
        ) {
    private static final int DEFAULT_PAGE_SIZE = 20;

    public PostSearchRequest {
        if (viewType == null) viewType = PostViewType.DEFAULT;
        if (size == null || size <= 0) size = DEFAULT_PAGE_SIZE;
    }

    public PostSearchRequest(
            String categoryCode,
            Long resortId,
            SearchType searchType,
            String keyword,
            PostViewType viewType,
            Integer page,
            String cursor,
            Integer size) {
        this(categoryCode, resortId, searchType, keyword, viewType, page, cursor, size, null);
    }

    public PostSearchRequest withWriterPublicId(String publicId) {
        return new PostSearchRequest(
                categoryCode,
                resortId,
                searchType,
                keyword,
                viewType,
                page,
                cursor,
                size,
                publicId);
    }
}
