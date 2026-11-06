package com.ikae.snowthing.domain.charactertest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.ikae.snowthing.domain.charactertest.entity.CharacterSport;
import com.ikae.snowthing.domain.charactertest.model.CharacterAnswers;
import com.ikae.snowthing.domain.member.validation.NicknamePolicy;

/** 모임 생성과 참여에 함께 쓴다. 금칙어 검사는 서비스에서 한다. */
public record CharacterJoinRequest(
        @NotNull(message = NicknamePolicy.MESSAGE)
                @Pattern(regexp = NicknamePolicy.REGEXP, message = NicknamePolicy.MESSAGE)
                String nickname,
        @NotNull(message = "스키 또는 보드를 골라 주세요.") CharacterSport sport,
        @NotNull(message = "답변을 입력해 주세요.")
                @Pattern(regexp = CharacterAnswers.REGEXP, message = "답변 형식이 올바르지 않습니다.")
                String answers) {}
