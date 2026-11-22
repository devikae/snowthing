package com.ikae.snowthing.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
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

    @Test
    @DisplayName("숨김 임계값이 검토 임계값보다 낮아도 숨김을 정한 점수는 근거로 남긴다")
    void hideScoreKeptBelowReviewThreshold() {
        ModerationProperties.Api lowHide =
                new ModerationProperties.Api(
                        true, "key", null, null, 0.5, 0.8, List.of("illicit"), 0, null, null);
        ModerationScoreRules.Result result =
                ModerationScoreRules.judge(Map.of("illicit", 0.6, "hate", 0.3), lowHide);
        assertThat(result.verdict()).isEqualTo(ModerationVerdict.HIDE);
        assertThat(result.notableScores()).containsOnlyKeys("illicit");
    }

    @Test
    @DisplayName("근거 JSON이 길이 제한을 넘으면 중간을 자르지 않고 점수를 빼서 규칙과 메모를 남긴다")
    void reasonsStayValidJson() {
        Map<String, Double> manyScores = new LinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            manyScores.put("category-with-a-long-name-" + i, 0.5);
        }
        String reasons =
                ModerationProcessor.reasons(List.of("CONTACT_TELEGRAM"), manyScores, "NOTE");
        assertThat(reasons).isEqualTo("{\"rules\":[\"CONTACT_TELEGRAM\"],\"note\":\"NOTE\"}");
    }

    private ModerationScoreRules.Result judge(Map<String, Double> scores) {
        return ModerationScoreRules.judge(scores, api);
    }
}
