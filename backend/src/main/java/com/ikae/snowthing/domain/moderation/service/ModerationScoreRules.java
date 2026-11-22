package com.ikae.snowthing.domain.moderation.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.ikae.snowthing.domain.moderation.config.ModerationProperties;
import com.ikae.snowthing.domain.moderation.entity.ModerationVerdict;

/**
 * 2단계 점수를 판정으로 바꾼다. 임계값은 설정에 둔다.
 *
 * <ul>
 *   <li>숨김 카테고리(기본 sexual, sexual/minors, illicit, illicit/violent) 중 하나라도 {@code hideThreshold}(기본
 *       0.9) 이상: HIDE
 *   <li>어느 카테고리든 {@code reviewThreshold}(기본 0.4) 이상: REVIEW
 *   <li>그 밖: PASS
 * </ul>
 */
public final class ModerationScoreRules {

    private ModerationScoreRules() {}

    public static Result judge(Map<String, Double> scores, ModerationProperties.Api api) {
        Set<String> hideCategories = api.hideCategorySet();
        ModerationVerdict verdict = ModerationVerdict.PASS;
        Map<String, Double> notable = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            double score = entry.getValue() == null ? 0 : entry.getValue();
            if (hideCategories.contains(entry.getKey()) && score >= api.hideThreshold()) {
                verdict = ModerationVerdict.HIDE;
            } else if (score >= api.reviewThreshold()) {
                verdict = verdict.max(ModerationVerdict.REVIEW);
            }
            if (score >= api.reviewThreshold()) {
                notable.put(entry.getKey(), Math.round(score * 1000) / 1000.0);
            }
        }
        return new Result(verdict, notable);
    }

    /**
     * @param notableScores 근거로 남길 점수 (검토 임계값 이상만, 소수 셋째 자리)
     */
    public record Result(ModerationVerdict verdict, Map<String, Double> notableScores) {}
}
