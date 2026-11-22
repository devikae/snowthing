package com.ikae.snowthing.domain.comment.entity;

/** 댓글의 금칙어 보류 상태 (IKA-96). 문자열로 저장하므로 이름을 바꾸지 않는다. 보류된 적 없는 댓글은 null이다. */
public enum CommentFilterState {
    HELD,
    REJECTED
}
