package com.ikae.snowthing.domain.aimember;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.LongStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ikae.snowthing.domain.aimember.AiActivityPlanner.Kind;
import com.ikae.snowthing.domain.aimember.AiActivityPlanner.Planned;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.PostView;
import com.ikae.snowthing.domain.aimember.AiReactionPolicy.Reaction;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.Brief;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.ComposeRequest;
import com.ikae.snowthing.domain.aimember.compose.AiComposeGateway.Context;
import com.ikae.snowthing.domain.aimember.persona.Persona;
import com.ikae.snowthing.domain.aimember.persona.Persona.Activity;
import com.ikae.snowthing.domain.aimember.persona.Persona.Window;

class AiMemberDiversityTest {

    private static final LocalDate DATE = LocalDate.of(2026, 12, 20);

    @Test
    @DisplayName("회원마다 자기 활동 시간대에만 글 시각이 생긴다")
    void plansInsideEachMemberWindows() {
        Map<Long, Activity> activities = new LinkedHashMap<>();
        activities.put(1L, activity(new Window(0, 2), 2.5));
        activities.put(2L, activity(new Window(10, 11), 2.5));

        for (int day = 0; day < 60; day++) {
            LocalDate date = DATE.plusDays(day);
            List<Planned> plan =
                    AiActivityPlanner.plan(
                            date, activities, Map.of(), date.atStartOfDay().minusMinutes(1));
            plan.stream()
                    .filter(action -> action.kind() == Kind.POST)
                    .forEach(
                            action -> {
                                int hour = action.at().getHour();
                                if (action.memberId() == 1L) {
                                    assertThat(hour).isBetween(0, 1);
                                } else {
                                    assertThat(hour).isEqualTo(10);
                                }
                            });
        }
    }

    @Test
    @DisplayName("하루 글 수의 평균이 postsPerDay에 가깝고 0이면 쓰지 않는다")
    void targetFollowsPostsPerDay() {
        SplittableRandom random = new SplittableRandom(7);
        double total = 0;
        for (int i = 0; i < 20_000; i++) {
            total += AiActivityPlanner.target(0.3, random);
        }
        assertThat(total / 20_000).isBetween(0.27, 0.33);
        for (int i = 0; i < 100; i++) {
            assertThat(AiActivityPlanner.target(0, random)).isZero();
            assertThat(AiActivityPlanner.target(2.5, random)).isBetween(0, 5);
        }
    }

    @Test
    @DisplayName("10명이어도 사용자 글 하나에 붙는 첫 댓글은 4명일 때와 비슷하다")
    void commentChanceScalesWithMemberCount() {
        assertThat(averageCommenters(10)).isBetween(1.6, 2.4);
        assertThat(averageCommenters(4)).isBetween(1.6, 2.4);
    }

    @Test
    @DisplayName("글마다 먼저 고르는 회원이 달라 앞번호에 몰리지 않는다")
    void reactionsAreNotBiasedToFirstMember() {
        List<Long> members = LongStream.rangeClosed(1, 10).boxed().toList();
        Set<Long> first = new HashSet<>();
        LocalDateTime later = LocalDateTime.of(2026, 12, 21, 12, 0);
        for (long postId = 1; postId <= 300; postId++) {
            AiReactionPolicy.next(
                            List.of(post(postId)), Map.of(), members, Map.of(), Set.of(), later)
                    .map(Reaction::memberId)
                    .ifPresent(first::add);
        }
        assertThat(first).hasSizeGreaterThanOrEqualTo(9);
    }

    @Test
    @DisplayName("활동 시간이 아닌 회원은 댓글을 달지 않는다")
    void inactiveMembersDoNotComment() {
        List<Long> members = LongStream.rangeClosed(1, 10).boxed().toList();
        LocalDateTime later = LocalDateTime.of(2026, 12, 21, 12, 0);
        for (long postId = 1; postId <= 200; postId++) {
            assertThat(
                            AiReactionPolicy.next(
                                            List.of(post(postId)),
                                            Map.of(),
                                            members,
                                            Map.of(),
                                            Set.of(),
                                            later,
                                            id -> id == 3L)
                                    .map(Reaction::memberId))
                    .matches(found -> found.isEmpty() || found.get() == 3L);
        }
    }

    @Test
    @DisplayName("글 형식과 길이가 여러 가지로 나오고 소재는 페르소나 목록에서 고른다")
    void briefsVary() {
        Persona persona =
                new Persona(
                        "a1",
                        "a1@snowthing.invalid",
                        "닉",
                        "211.36.10.20",
                        "설명",
                        new Activity(
                                List.of(new Window(7, 9)),
                                1,
                                Map.of("FREE", 3, "QNA", 1),
                                List.of("왁싱", "부츠")));
        SplittableRandom random = new SplittableRandom(3);
        Set<String> lengths = new HashSet<>();
        Set<String> topics = new HashSet<>();
        Set<String> boards = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            Brief brief = AiComposeBriefs.post(persona, "FREE", random);
            lengths.add(brief.length());
            topics.add(brief.topic());
            boards.add(AiComposeBriefs.board(persona, random));
            lengths.add(AiComposeBriefs.comment(random).length());
        }
        assertThat(lengths).hasSizeGreaterThanOrEqualTo(8);
        assertThat(topics).containsExactlyInAnyOrder("왁싱", "부츠");
        assertThat(boards).containsExactlyInAnyOrder("FREE", "QNA");
    }

    @Test
    @DisplayName("유사도: 어미만 바꾼 문장은 잡고 다른 글과 짧은 글은 통과시킨다")
    void similarity() {
        List<String> titles = List.of("개장 초반 리프트 대기 줄 어느 시간대가 낫나요");

        assertThat(
                        AiTextSimilarity.tooSimilar(
                                "개장 초반 리프트 대기 줄, 어느 시간대가 나을까요?",
                                titles,
                                AiTextSimilarity.TITLE_THRESHOLD))
                .isTrue();
        assertThat(
                        AiTextSimilarity.tooSimilar(
                                "데크 왁싱 집에서 하시는 분", titles, AiTextSimilarity.TITLE_THRESHOLD))
                .isFalse();
        assertThat(AiTextSimilarity.tooSimilar("ㅇㅈ", List.of("ㄴㄴ"), 0.5)).isFalse();
        assertThat(AiTextSimilarity.tooSimilar("ㅇㅈ", List.of("ㅇㅈ!"), 0.5)).isTrue();
    }

    @Test
    @DisplayName("봇 서버 검증이 null을 받지 않으므로 소재 없는 지시와 채팅 요청에 null을 넣지 않는다")
    void omitsNullsInComposeJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Context context =
                new Context("2026-12-20", "시즌 한창", List.of(), List.of(), List.of(), List.of());

        String comment =
                mapper.writeValueAsString(
                        new ComposeRequest(
                                "id",
                                "COMMENT",
                                List.of(),
                                "FREE",
                                null,
                                null,
                                null,
                                context,
                                new Brief(null, "한 줄", "15자 이내")));
        String chat =
                mapper.writeValueAsString(
                        new ComposeRequest(
                                "id", "CHAT", List.of(), null, null, List.of(), 2, context, null));

        assertThat(comment).doesNotContain("null").contains("\"form\":\"한 줄\"");
        assertThat(chat).doesNotContain("null").doesNotContain("brief");
    }

    private static double averageCommenters(int memberCount) {
        List<Long> members = LongStream.rangeClosed(1, memberCount).boxed().toList();
        LocalDateTime later = LocalDateTime.of(2026, 12, 21, 12, 0);
        int total = 0;
        int posts = 3_000;
        for (long postId = 1; postId <= posts; postId++) {
            Set<String> attempted = new HashSet<>();
            while (true) {
                var reaction =
                        AiReactionPolicy.next(
                                List.of(post(postId)),
                                Map.of(),
                                members,
                                Map.of(),
                                attempted,
                                later);
                if (reaction.isEmpty()) {
                    break;
                }
                attempted.add(reaction.get().key());
                total++;
            }
        }
        return (double) total / posts;
    }

    private static PostView post(long postId) {
        return new PostView(
                postId,
                "public-" + postId,
                1_000L,
                "FREE",
                "제목",
                "본문",
                LocalDateTime.of(2026, 12, 20, 12, 0));
    }

    private static Activity activity(Window window, double postsPerDay) {
        return new Activity(List.of(window), postsPerDay, Map.of("FREE", 1), List.of());
    }
}
