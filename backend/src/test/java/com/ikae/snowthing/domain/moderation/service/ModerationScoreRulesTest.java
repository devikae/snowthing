package com.ikae.snowthing.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.moderation.config.ModerationProperties;
import com.ikae.snowthing.domain.moderation.entity.ModerationVerdict;

/** 2단계 점수 임계값 분기 (IKA-80). 기본값: 숨김 카테고리 0.9 이상 HIDE, 어느 카테고리든 0.4 이상 REVIEW. */
class ModerationScoreRulesTest {

    private final ModerationProperties.Api api =
            new ModerationProperties.Api(true, "key", null, null, 0, 0, null, 0, null, null);

    @Test
    @DisplayName("숨김 카테고리가 0.9 이상이면 HIDE")
    void hide() {
        assertThat(judge(Map.of("sexual/minors", 0.91, "harassment", 0.1)).verdict())
                .isEqualTo(ModerationVerdict.HIDE);
        assertThat(judge(Map.of("illicit", 0.9)).verdict()).isEqualTo(ModerationVerdict.HIDE);
    }

    @Test
    @DisplayName("숨김 카테고리가 아니면 0.99여도 REVIEW까지만 간다")
    void otherCategoryReview() {
        ModerationScoreRules.Result result = judge(Map.of("harassment", 0.99));
        assertThat(result.verdict()).isEqualTo(ModerationVerdict.REVIEW);
        assertThat(result.notableScores()).containsEntry("harassment", 0.99);
    }

    @Test
    @DisplayName("0.4 이상 0.9 미만은 REVIEW")
    void middleReview() {
        assertThat(judge(Map.of("sexual", 0.4)).verdict()).isEqualTo(ModerationVerdict.REVIEW);
        assertThat(judge(Map.of("illicit", 0.89)).verdict()).isEqualTo(ModerationVerdict.REVIEW);
    }

    @Test
    @DisplayName("모두 0.4 미만이면 PASS이고 근거 점수도 남기지 않는다")
    void pass() {
        ModerationScoreRules.Result result = judge(Map.of("sexual", 0.39, "violence", 0.01));
        assertThat(result.verdict()).isEqualTo(ModerationVerdict.PASS);
        assertThat(result.notableScores()).isEmpty();
    }

    @Test
    @DisplayName("임계값과 숨김 카테고리는 설정을 따른다")
    void configurable() {
        ModerationProperties.Api strict =
                new ModerationProperties.Api(
                        true, "key", null, null, 0.5, 0.2, List.of("violence"), 0, null, null);
        assertThat(ModerationScoreRules.judge(Map.of("violence", 0.6), strict).verdict())
                .isEqualTo(ModerationVerdict.HIDE);
        assertThat(ModerationScoreRules.judge(Map.of("sexual", 0.95), strict).verdict())
                .isEqualTo(ModerationVerdict.REVIEW);
        assertThat(ModerationScoreRules.judge(Map.of("hate", 0.25), strict).verdict())
                .isEqualTo(ModerationVerdict.REVIEW);
    }

    private ModerationScoreRules.Result judge(Map<String, Double> scores) {
        return ModerationScoreRules.judge(scores, api);
    }
}
