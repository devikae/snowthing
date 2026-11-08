package com.ikae.snowthing.domain.search.repository;

import java.util.stream.Collectors;

import com.ikae.snowthing.domain.post.entity.PostCategoryCodes;

/**
 * 근거 검색에서 쓰는 공개 범위 조건. 게시판 목록({@code PostRepositoryCustomImpl})과 같은 기준이다.
 *
 * <p>재구축(색인 대상 선정), 검색(후보 생성), 근거 조회(원문 재검사)가 모두 이 조건을 쓴다. 기준을 한 곳에 두어 셋이 어긋나지 않게 한다. 별칭은 post = p,
 * post_category = pc, comment = cm 으로 고정한다.
 *
 * <p>제외 카테고리는 {@link PostCategoryCodes#HIDDEN_FROM_PUBLIC}에서 만든다. 코드 상수만 들어가므로 SQL에 직접 넣어도 된다.
 */
final class SearchVisibilitySql {

    static final String POST_VISIBLE =
            "p.status = 'NORMAL' AND p.is_deleted = false AND pc.code NOT IN ("
                    + PostCategoryCodes.HIDDEN_FROM_PUBLIC.stream()
                            .map(code -> "'" + code + "'")
                            .collect(Collectors.joining(", "))
                    + ")";

    static final String COMMENT_VISIBLE = "cm.is_deleted = false";

    private SearchVisibilitySql() {}
}
