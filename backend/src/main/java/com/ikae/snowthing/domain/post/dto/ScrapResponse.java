package com.ikae.snowthing.domain.post.dto;

/** 스크랩 추가, 취소 결과. changed는 이번 요청으로 상태가 바뀌었는지다. */
public record ScrapResponse(boolean scrapped, boolean changed) {}
