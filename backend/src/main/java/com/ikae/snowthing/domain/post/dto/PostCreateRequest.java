package com.ikae.snowthing.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

@Builder
public record PostCreateRequest(
    @NotBlank(message = "카테고리 코드는 필수 입력값입니다.")
    String categoryCode,

    @NotBlank(message = "제목은 필수 입력값입니다.")
    @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다.")
    String title,

    @NotBlank(message = "본문은 필수 입력값입니다.")
    String content,

    boolean isAnonymous,
    String anonymousPassword,
    List<String> imageUrls
) {
    public PostCreateRequest {
        if (imageUrls == null) {
            imageUrls = List.of();
        } else {
            imageUrls = List.copyOf(imageUrls);
        }
    }
}
