package com.ikae.snowthing.domain.post.dto;

import com.ikae.snowthing.domain.post.entity.AnonymousMode;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.WritePermission;

/** 열린 게시판 한 줄 (IKA-114). 메뉴, 홈 탭, 글쓰기 선택지, 게시판 설명이 쓴다. */
public record BoardResponse(
        String code,
        String name,
        String shortName,
        String description,
        boolean showOnHome,
        WritePermission writePermission,
        AnonymousMode anonymousMode) {

    public static BoardResponse from(PostCategory category) {
        return new BoardResponse(
                category.getCode(),
                category.getName(),
                category.getShortName(),
                category.getDescription(),
                category.isShowOnHome(),
                category.getWritePermission(),
                category.getAnonymousMode());
    }
}
