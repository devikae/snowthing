package com.ikae.snowthing.domain.contentfilter.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ikae.snowthing.domain.contentfilter.entity.FilterTargetType;

/**
 * 관리자 금칙어 보류 검토 항목. 관리자 화면 전용이라 작성자 IP를 가리지 않는다. 본문은 태그를 지운 앞부분만 준다.
 *
 * @param commentId 댓글 보류일 때만 값이 있다
 * @param writerNickname 회원이 쓴 글이면 닉네임(익명으로 썼어도 관리자에게는 보인다), 비회원이면 null
 */
public record FilterHoldItem(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        FilterTargetType targetType,
        String postPublicId,
        @JsonSerialize(using = ToStringSerializer.class) Long commentId,
        String categoryCode,
        String postTitle,
        String excerpt,
        String matchedWord,
        String writerNickname,
        String writerPublicId,
        boolean anonymous,
        String writerIp,
        LocalDateTime heldAt) {}
