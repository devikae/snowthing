package com.ikae.snowthing.domain.post.repository;

import com.ikae.snowthing.domain.post.dto.PostListResponse;
import com.ikae.snowthing.domain.post.dto.PostSearchRequest;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;

public interface PostRepositoryCustom {

    /** 웹 Offset 기반 동적 목록 조회 (Hard Cap 100페이지 한정 및 커버링 인덱스) */
    CursorPageResponse<PostListResponse> findPostsByOffset(PostSearchRequest request);

    /** 모바일 Keyset Cursor 기반 동적 목록 조회 ($O(1)$ 탐색) */
    CursorPageResponse<PostListResponse> findPostsByCursor(PostSearchRequest request);
}
