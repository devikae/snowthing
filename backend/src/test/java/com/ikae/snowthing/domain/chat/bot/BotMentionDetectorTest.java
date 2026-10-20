package com.ikae.snowthing.domain.chat.bot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BotMentionDetectorTest {

    private final BotMentionDetector detector = new BotMentionDetector();

    @Test
    void extractsQuestionOnlyFromLeadingBotMention() {
        assertThat(detector.questionFrom("@bot 오늘 설질 어때?")).contains("오늘 설질 어때?");
        assertThat(detector.questionFrom("일반 대화 @bot 질문")).isEmpty();
        assertThat(detector.questionFrom("@bot")).isEmpty();
    }
}
