package com.ikae.snowthing.domain.post.entity;

/** 게시판의 익명 글 규칙 (IKA-114). FORCED는 항상 익명, OPTIONAL은 작성자가 고르고, FORBIDDEN은 익명을 막는다. */
public enum AnonymousMode {
    FORCED,
    OPTIONAL,
    FORBIDDEN
}
