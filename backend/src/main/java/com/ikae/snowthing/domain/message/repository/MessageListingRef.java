package com.ikae.snowthing.domain.message.repository;

/** 쪽지 화면에 보여 줄 판매글 요약. 게시글 publicId와 제목만 담는다. */
public record MessageListingRef(Long marketListingId, String postPublicId, String title) {}
