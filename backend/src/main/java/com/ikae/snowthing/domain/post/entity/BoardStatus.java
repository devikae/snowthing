package com.ikae.snowthing.domain.post.entity;

/** 게시판 공개 상태 (IKA-114). 닫힌 게시판은 메뉴, 홈 탭, 글쓰기에서 빠지고 새 글을 막는다. 기존 글은 계속 보인다. */
public enum BoardStatus {
    OPEN,
    HIDDEN
}
