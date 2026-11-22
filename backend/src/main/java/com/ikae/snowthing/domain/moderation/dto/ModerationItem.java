package com.ikae.snowthing.domain.moderation.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ikae.snowthing.domain.moderation.entity.ModerationStage;
import com.ikae.snowthing.domain.moderation.entity.ModerationTargetType;
import com.ikae.snowthing.domain.moderation.entity.ModerationVerdict;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository.Row;
import com.ikae.snowthing.domain.moderation.repository.ModerationTargetQueryRepository.TargetState;
import com.ikae.snowthing.domain.moderation.repository.ModerationTargetQueryRepository.TargetSummary;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 관리자 검토 대기 항목 (IKA-80). 관리자 화면 전용이다. 근거는 사람이 읽는 짧은 말로 바꿔 준다.
 *
 * @param targetId 대상 번호 (글이면 글 번호, 댓글이면 댓글 번호)
 * @param postPublicId 원문 링크용. 댓글이면 댓글이 달린 글. 설질 제보는 null
 * @param targetState 지금 원문 상태 (VISIBLE 보임, HIDDEN 가려짐, DELETED 지워짐)
 * @param reasons 걸린 규칙과 점수
 */
public record ModerationItem(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        ModerationTargetType targetType,
        @JsonSerialize(using = ToStringSerializer.class) Long targetId,
        ModerationVerdict verdict,
        ModerationStage stage,
        boolean failed,
        List<String> reasons,
        String postPublicId,
        String categoryCode,
        String title,
        String excerpt,
        TargetState targetState,
        String writerNickname,
        LocalDateTime createdAt,
        LocalDateTime processedAt) {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Map<String, String> LABELS =
            Map.ofEntries(
                    Map.entry("BANNED_WORD", "금칙어"),
                    Map.entry("CONTACT_TELEGRAM", "텔레그램 연락"),
                    Map.entry("CONTACT_OPEN_CHAT", "오픈채팅 링크"),
                    Map.entry("CONTACT_KAKAO_ID", "카톡 아이디"),
                    Map.entry("CONTACT_SHORT_URL", "단축 주소"),
                    Map.entry("DUPLICATE", "같은 내용 반복"),
                    Map.entry("NEW_ACCOUNT_LINKS", "새 계정 링크 여러 개"),
                    Map.entry("POLITICAL", "정치 글"),
                    Map.entry("API_FAILED", "자동 검사 실패"),
                    Map.entry("CHECK_FAILED", "자동 검사 실패"));

    public static ModerationItem of(Row row, TargetSummary target) {
        return new ModerationItem(
                row.id(),
                row.targetType(),
                row.targetId(),
                row.verdict(),
                row.stage(),
                row.status() == com.ikae.snowthing.domain.moderation.entity.ModerationStatus.FAILED,
                readableReasons(row.reasons()),
                target == null ? null : target.postPublicId(),
                target == null ? null : target.categoryCode(),
                target == null ? null : target.title(),
                target == null ? "" : target.excerpt(),
                target == null ? TargetState.DELETED : target.state(),
                target == null ? null : target.writerNickname(),
                row.createdAt(),
                row.processedAt());
    }

    /** 근거 JSON을 짧은 말로 바꾼다. 형식이 깨졌으면 빈 목록. */
    static List<String> readableReasons(String json) {
        List<String> reasons = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return reasons;
        }
        try {
            JsonNode root = JSON.readTree(json);
            for (JsonNode rule : root.path("rules")) {
                reasons.add(LABELS.getOrDefault(rule.asString(), rule.asString()));
            }
            for (Map.Entry<String, JsonNode> score : root.path("scores").properties()) {
                reasons.add(score.getKey() + " " + score.getValue().asString());
            }
            String note = root.path("note").asString("");
            if (LABELS.containsKey(note)) {
                reasons.add(LABELS.get(note));
            }
        } catch (RuntimeException ignored) {
            // 근거가 잘렸거나 깨졌으면 목록만 비운다. 항목 처리는 그대로 할 수 있다.
        }
        return reasons;
    }
}
