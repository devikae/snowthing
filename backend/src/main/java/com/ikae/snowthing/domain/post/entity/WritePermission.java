package com.ikae.snowthing.domain.post.entity;

/** 게시판에 글을 쓸 수 있는 사람 (IKA-114). ALL은 비회원 포함이라 익명 규칙이 FORCED여야 한다. */
public enum WritePermission {
    ALL,
    MEMBER,
    ADMIN
}
