package com.ikae.snowthing.domain.post.entity;

public enum PostStatus {
    NORMAL,
    DELETED,
    BLOCKED,
    HIDDEN,
    DRAFT,
    TEMPORARY_HOLD,
    /**
     * 금칙어가 걸려 조용히 보류한 글 (IKA-96). 신고 임시조치(TEMPORARY_HOLD)와 달리 작성자에게 알리지 않는다. 공개 목록과 검색은 NORMAL만 보여
     * 주므로 다른 사람에게는 보이지 않고, 작성자 본인과 관리자만 상세를 본다. 응답에는 {@link #publicView()}로 NORMAL처럼 내보낸다.
     */
    FILTER_HOLD;

    /** 작성자에게 보내는 응답에서 금칙어 보류를 드러내지 않는다. */
    public PostStatus publicView() {
        return this == FILTER_HOLD ? NORMAL : this;
    }
}
