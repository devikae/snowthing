package com.ikae.snowthing.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageRequest(
        String resortTag,
        @NotBlank(message = "공백 메시지는 전송할 수 없습니다.")
                @Size(min = 1, max = 100, message = "메시지는 최대 100자까지 작성할 수 있습니다.")
                String content) {}
