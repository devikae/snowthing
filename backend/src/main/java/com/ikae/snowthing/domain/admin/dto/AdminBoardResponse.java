package com.ikae.snowthing.domain.admin.dto;

import java.time.LocalDateTime;

import com.ikae.snowthing.domain.post.entity.AnonymousMode;
import com.ikae.snowthing.domain.post.entity.BoardStatus;
import com.ikae.snowthing.domain.post.entity.PostCategory;
import com.ikae.snowthing.domain.post.entity.WritePermission;

/** 관리자 게시판 목록 한 줄 (IKA-114). 닫힌 게시판과 삭제한 게시판도 들어간다. */
public record AdminBoardResponse(
        String code,
        String name,
        String shortName,
        String description,
        int sortOrder,
        BoardStatus status,
        boolean showOnHome,
        WritePermission writePermission,
        AnonymousMode anonymousMode,
        LocalDateTime deletedAt) {

    public static AdminBoardResponse from(PostCategory category) {
        return new AdminBoardResponse(
                category.getCode(),
                category.getName(),
                category.getShortName(),
                category.getDescription(),
                category.getSortOrder(),
                category.getStatus(),
                category.isShowOnHome(),
                category.getWritePermission(),
                category.getAnonymousMode(),
                category.getDeletedAt());
    }
}
