package com.ikae.snowthing.domain.post.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Builder;

@Builder
public record PostCreateRequest(
        @NotBlank(message = "카테고리 코드는 필수 입력값입니다.") String categoryCode,
        @NotBlank(message = "제목은 필수 입력값입니다.") @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
                String title,
        @NotBlank(message = "본문은 필수 입력값입니다.") String content,
        boolean isAnonymous,
        String anonymousPassword,
        @Size(max = 10, message = "첨부 사진은 최대 10장까지 등록할 수 있습니다.") List<String> imageUrls) {
    public PostCreateRequest {
        if (imageUrls == null) {
            imageUrls = List.of();
        } else {
            imageUrls = List.copyOf(imageUrls);
        }
    }
}
