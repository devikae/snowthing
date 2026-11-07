package com.ikae.snowthing.domain.post.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Builder;

@Builder
public record PostUpdateRequest(
        @NotBlank(message = "카테고리 코드는 필수 입력값입니다.") String categoryCode,
        @NotBlank(message = "제목은 필수 입력값입니다.") @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
                String title,
        @NotBlank(message = "본문은 필수 입력값입니다.")
                @Size(max = 100000, message = "본문은 최대 100,000자까지 입력 가능합니다.")
                String content,
        String anonymousPassword,
        @Size(max = 10, message = "첨부 사진은 최대 10장까지 등록할 수 있습니다.") List<String> imageUrls) {
    public PostUpdateRequest {
        if (imageUrls != null) {
            imageUrls = List.copyOf(imageUrls);
        }
    }
}
