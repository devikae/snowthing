package com.ikae.snowthing.domain.contentfilter.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 길이는 컬럼(50자)에 맞춘다. 기호와 공백을 뺀 길이는 서비스가 한 번 더 본다. */
public record BannedWordCreateRequest(@NotBlank @Size(max = 50) String word) {}
