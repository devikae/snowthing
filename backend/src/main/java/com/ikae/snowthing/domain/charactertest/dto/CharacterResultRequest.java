package com.ikae.snowthing.domain.charactertest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import com.ikae.snowthing.domain.charactertest.model.CharacterAnswers;

public record CharacterResultRequest(
        @NotNull(message = "답변을 입력해 주세요.")
                @Pattern(regexp = CharacterAnswers.REGEXP, message = "답변 형식이 올바르지 않습니다.")
                String answers) {}
