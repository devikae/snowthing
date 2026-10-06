package com.ikae.snowthing.domain.resortreport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ResortReportCreateRequest {

    @NotNull(message = "리조트를 선택해 주세요.")
    private Long resortId;

    @NotBlank(message = "설질 제보 내용을 입력해 주세요.")
    @Size(max = 100, message = "설질 제보 내용은 최대 100자까지 작성할 수 있습니다.")
    private String content;
}
