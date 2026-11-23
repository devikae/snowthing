package com.ikae.snowthing.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ikae.snowthing.domain.post.entity.AnonymousMode;
import com.ikae.snowthing.domain.post.entity.WritePermission;

/**
 * 게시판 추가와 수정 요청 (IKA-114). 코드는 추가할 때만 읽고 수정에서는 무시한다. 주소와 검색 유입이 코드에 묶여 있어 바꾸지 않는다.
 *
 * <p>코드 형식과 예약 코드, "누구나면 항상 익명" 규칙은 서비스에서 검사해 화면에 알맞은 문구를 준다.
 */
public record AdminBoardRequest(
        String code,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 10) String shortName,
        @Size(max = 200) String description,
        boolean showOnHome,
        @NotNull WritePermission writePermission,
        @NotNull AnonymousMode anonymousMode) {}
