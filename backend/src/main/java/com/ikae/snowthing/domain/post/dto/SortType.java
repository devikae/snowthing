package com.ikae.snowthing.domain.post.dto;

public enum SortType {
    LATEST, // 최신순 (created_at DESC, id DESC)
    POPULAR // 추천순/인기순 (like_count DESC, id DESC)
}
