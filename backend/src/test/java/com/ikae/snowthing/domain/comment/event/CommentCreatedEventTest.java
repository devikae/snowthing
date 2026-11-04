package com.ikae.snowthing.domain.comment.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommentCreatedEventTest {

    @Test
    @DisplayName("미리보기는 공백을 한 칸으로 줄이고 앞 50자만 남긴다")
    void previewCollapsesWhitespaceAndCuts() {
        assertThat(CommentCreatedEvent.preview("  첫 줄\n\n둘째   줄  ")).isEqualTo("첫 줄 둘째 줄");
        assertThat(CommentCreatedEvent.preview("가".repeat(80))).isEqualTo("가".repeat(50));
    }

    @Test
    @DisplayName("이모지는 반으로 잘리지 않는다")
    void previewKeepsSurrogatePairs() {
        String preview = CommentCreatedEvent.preview("😀".repeat(60));

        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(50);
        assertThat(preview).isEqualTo("😀".repeat(50));
    }
}
