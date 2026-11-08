package com.ikae.snowthing.domain.post.repository;

import java.util.Collection;
import java.util.Set;

import com.ikae.snowthing.domain.post.dto.PostListResponse;
import com.ikae.snowthing.domain.post.dto.PostSearchRequest;
import com.ikae.snowthing.global.common.dto.CursorPageResponse;

public interface PostRepositoryCustom {

    /** 웹 Offset 기반 동적 목록 조회 (Hard Cap 100페이지 한정 및 커버링 인덱스) */
    CursorPageResponse<PostListResponse> findPostsByOffset(PostSearchRequest request);

    /** 모바일 Keyset Cursor 기반 동적 목록 조회 ($O(1)$ 탐색) */
    CursorPageResponse<PostListResponse> findPostsByCursor(PostSearchRequest request);

    /** 관리자가 삭제되지 않은 댓글을 남긴 게시글의 publicId. 1:1 문의 답변 상태 계산용 */
    Set<String> findPublicIdsAnsweredByAdmin(Collection<String> postPublicIds);
}
