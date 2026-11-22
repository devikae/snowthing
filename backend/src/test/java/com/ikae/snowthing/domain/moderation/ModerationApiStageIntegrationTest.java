package com.ikae.snowthing.domain.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.ikae.snowthing.domain.member.entity.Member;
import com.ikae.snowthing.domain.member.entity.Role;
import com.ikae.snowthing.domain.moderation.api.ModerationApiClient;
import com.ikae.snowthing.domain.moderation.api.ModerationApiException;

/**
 * 2단계(Moderation API) 분기와 재시도 (IKA-80). API 클라이언트를 가짜로 바꿔 점수를 정해 준다. 실제 OpenAI는 부르지 않는다.
 *
 * <p>가짜 클라이언트는 입력 글에 든 표시 말("HIGH", "MID")로 점수를 고른다. 입력이 제목과 본문뿐인지도 함께 본다.
 */
@SpringBootTest(
        properties = {
            "snowthing.moderation.api.enabled=true",
            "snowthing.moderation.api.api-key=test-key"
        })
@AutoConfigureMockMvc
class ModerationApiStageIntegrationTest extends ModerationTestSupport {

    @MockitoBean private ModerationApiClient apiClient;

    private Member writer;
    private MockHttpSession writerSession;
    private final List<String> sentInputs = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        category("FREE", "자유게시판");
        given(apiClient.moderate(anyList())).willReturn(List.of());
        drain();
        reset(apiClient);
        writer = member("api", Role.ROLE_USER);
        writerSession = login(writer);
        given(apiClient.moderate(anyList()))
                .willAnswer(
                        invocation -> {
                            List<String> inputs = invocation.getArgument(0);
                            sentInputs.addAll(inputs);
                            return inputs.stream().map(this::scoresFor).toList();
                        });
    }

    @Test
    @DisplayName("점수가 높으면 숨기고, 중간이면 검토 대기, 낮으면 통과한다. 보내는 글은 제목과 본문뿐이다")
    void thresholds() throws Exception {
        long high = writePost(writerSession, "HIGH 높은 점수", "숨김 카테고리 점수가 높은 글입니다");
        long mid = writePost(writerSession, "MID 중간 점수", "검토 대기 점수가 나오는 글입니다");
        long low = writePost(writerSession, "낮은 점수", "평범한 설질 후기 글입니다 정말로요");
        drain();

        assertThat(latest("POST", high))
                .containsEntry("verdict", "HIDE")
                .containsEntry("stage", "MODERATION_API");
        assertThat((String) latest("POST", high).get("reasons")).contains("sexual");
        assertThat(postStatus(high)).isEqualTo("BLOCKED");
        assertThat(latest("POST", mid)).containsEntry("verdict", "REVIEW");
        assertThat(postStatus(mid)).isEqualTo("NORMAL");
        assertThat(latest("POST", low))
                .containsEntry("verdict", "PASS")
                .containsEntry("stage", "MODERATION_API");

        assertThat(sentInputs)
                .anyMatch(input -> input.contains("HIGH 높은 점수") && input.contains("숨김 카테고리"));
        assertThat(sentInputs)
                .noneMatch(
                        input ->
                                input.contains(writer.getNickname())
                                        || input.contains(writer.getEmail())
                                        || input.contains(WRITER_IP));
    }

    @Test
    @DisplayName("규칙에서 검토 대기가 나오면 점수가 낮아도 검토 대기로 남는다")
    void ruleVerdictIsKept() throws Exception {
        long postId = writePost(writerSession, "연락", "문의는 t.me/keep 으로 주세요 감사합니다");
        drain();

        assertThat(latest("POST", postId))
                .containsEntry("verdict", "REVIEW")
                .containsEntry("stage", "MODERATION_API");
    }

    @Test
    @DisplayName("규칙에 걸리지 않은 짧은 글은 2단계를 부르지 않는다")
    void shortCleanTextSkipsApi() throws Exception {
        long postId = writePost(otherWriter(), "짧", "좋아요");
        drain();

        assertThat(latest("POST", postId))
                .containsEntry("verdict", "PASS")
                .containsEntry("stage", "RULE");
        verify(apiClient, never()).moderate(anyList());
    }

    @Test
    @DisplayName("2단계가 실패하면 다음 시각으로 미뤄 다시 하고, 다섯 번 실패하면 FAILED로 두고 검토 대기로 보낸다")
    void retriesThenFailsToReview() throws Exception {
        reset(apiClient);
        given(apiClient.moderate(anyList()))
                .willThrow(new ModerationApiException("moderation request failed: HTTP 429"));
        long postId = writePost(writerSession, "재시도", "점수를 받지 못하는 평범한 글입니다");

        drain();
        Map<String, Object> first = latest("POST", postId);
        assertThat(first).containsEntry("status", "PENDING").containsEntry("attempt_count", 1);
        assertThat((LocalDateTime) first.get("next_attempt_at")).isAfter(LocalDateTime.now());

        for (int attempt = 2; attempt <= 5; attempt++) {
            jdbc.update(
                    "UPDATE content_moderation SET next_attempt_at = ? WHERE target_type = 'POST'"
                            + " AND target_id = ?",
                    LocalDateTime.now().minusSeconds(1),
                    postId);
            drain();
        }

        Map<String, Object> last = latest("POST", postId);
        assertThat(last)
                .containsEntry("status", "FAILED")
                .containsEntry("verdict", "REVIEW")
                .containsEntry("attempt_count", 5);
        assertThat((String) last.get("reasons")).contains("API_FAILED");
        assertThat(postStatus(postId)).isEqualTo("NORMAL");
    }

    private MockHttpSession otherWriter() throws Exception {
        return login(member("short", Role.ROLE_USER));
    }

    private Map<String, Double> scoresFor(String input) {
        if (input.contains("HIGH")) {
            return Map.of("sexual", 0.97, "harassment", 0.02);
        }
        if (input.contains("MID")) {
            return Map.of("harassment", 0.55, "sexual", 0.01);
        }
        return Map.of("sexual", 0.01, "harassment", 0.01);
    }
}
