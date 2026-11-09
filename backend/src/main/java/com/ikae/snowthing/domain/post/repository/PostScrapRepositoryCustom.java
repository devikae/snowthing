package com.ikae.snowthing.domain.post.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.ikae.snowthing.domain.post.entity.PostScrap;

public interface PostScrapRepositoryCustom {

    /**
     * 회원의 스크랩을 최근 스크랩 순(시각, ID 내림차순)으로 limit개 가져온다. 삭제, 임시조치된 글과 게시판 목록에 나오지 않는 글은 뺀다. 커서 값이 없으면
     * 처음부터 읽는다.
     */
    List<PostScrap> findVisibleByMember(
            Long memberId, LocalDateTime cursorCreatedAt, Long cursorId, int limit);
}
