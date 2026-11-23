package com.ikae.snowthing.domain.post.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.post.entity.PostStatus;

class PostListResponseTest {

    @Test
    @DisplayName("고른 컬럼으로 만드는 목록 응답도 금칙어 보류 상태를 NORMAL로 내보낸다")
    void hidesFilterHoldStatus() {
        PostListResponse response =
                PostListResponse.of(
                        "post-1",
                        "자유",
                        "FREE",
                        "제목",
                        true,
                        null,
                        "203.0.113.7",
                        false,
                        0,
                        0,
                        0,
                        0,
                        PostStatus.FILTER_HOLD,
                        false,
                        LocalDateTime.of(2026, 11, 24, 9, 0));

        assertThat(response.status()).isEqualTo(PostStatus.NORMAL);
    }
}
