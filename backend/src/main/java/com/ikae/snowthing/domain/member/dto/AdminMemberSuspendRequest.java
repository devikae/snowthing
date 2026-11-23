package com.ikae.snowthing.domain.member.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 회원 정지 요청 (IKA-115). 본문이 없거나 days가 null이면 해제할 때까지 무기한이다.
 *
 * @param days 정지 일수. 1일부터 365일까지
 * @param reason 관리자 화면에만 보이는 사유
 */
public record AdminMemberSuspendRequest(
        @Min(1) @Max(365) Integer days, @Size(max = 200) String reason) {}
