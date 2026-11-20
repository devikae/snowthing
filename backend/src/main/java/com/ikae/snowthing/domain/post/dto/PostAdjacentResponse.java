package com.ikae.snowthing.domain.post.dto;

/** 게시글 상세의 이전글, 다음글. 같은 게시판에서 작성 순서로 바로 앞(이전글)과 바로 뒤(다음글) 글이다. 없으면 null이다. */
public record PostAdjacentResponse(Item previous, Item next) {

    public static final PostAdjacentResponse NONE = new PostAdjacentResponse(null, null);

    public record Item(String publicId, String title) {}
}
