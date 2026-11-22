package com.ikae.snowthing.domain.slope.entity;

/** 기준 슬로프에서 본 다른 슬로프와의 관계. */
public enum SlopeRelation {
    /** 다른 슬로프가 이 슬로프로 들어온다. */
    MERGE,
    /** 다른 슬로프가 이 슬로프에서 갈라져 나간다. */
    BRANCH,
    /** 이 슬로프가 끝나고 다른 슬로프로 이어진다. MERGE의 반대쪽이다. */
    CONTINUE,
    /** 이 슬로프가 다른 슬로프 중간에서 갈라져 시작한다. BRANCH의 반대쪽이다. */
    STARTS_FROM,
    /** 같은 곳에서 출발한다. */
    SHARE_START,
    /** 같은 곳에서 끝난다. */
    SHARE_END,
    /** 중간에서 가로지른다. */
    CROSS,
    /** 붙어 있지는 않지만 가까이 나란히 있다. */
    PARALLEL
}
