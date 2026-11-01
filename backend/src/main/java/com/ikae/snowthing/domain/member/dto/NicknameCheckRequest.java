package com.ikae.snowthing.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.ikae.snowthing.domain.member.validation.NicknamePolicy;

public record NicknameCheckRequest(
        @NotBlank @Pattern(regexp = NicknamePolicy.REGEXP, message = NicknamePolicy.MESSAGE)
                String nickname) {}
