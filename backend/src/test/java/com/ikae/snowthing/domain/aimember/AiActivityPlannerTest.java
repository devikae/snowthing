package com.ikae.snowthing.domain.aimember;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.aimember.AiActivityPlanner.Kind;
import com.ikae.snowthing.domain.aimember.AiActivityPlanner.Planned;

class AiActivityPlannerTest {

    private static final List<Long> MEMBERS = List.of(1L, 2L, 3L, 4L);

    @Test
    @DisplayName("활동 시각은 출퇴근·점심·저녁 시간대에만 생기고 새벽에는 없다")
    void plansOnlyInsideActiveWindows() {
        for (int day = 0; day < 60; day++) {
            LocalDate date = LocalDate.of(2026, 12, 1).plusDays(day);

            List<Planned> planned =
                    AiActivityPlanner.plan(date, MEMBERS, Map.of(), date.atStartOfDay());

            assertThat(planned)
                    .allSatisfy(
                            action -> {
                                assertThat(AiActivityPlanner.isActive(action.at())).isTrue();
                                assertThat(action.at().toLocalDate()).isEqualTo(date);
                            });
        }
    }

    @Test
    @DisplayName("캐릭터마다 글은 0~5개, 채팅은 하루 5번이다")
    void plansPostAndChatCounts() {
        LocalDate date = LocalDate.of(2026, 12, 20);

        List<Planned> planned =
                AiActivityPlanner.plan(date, MEMBERS, Map.of(), date.atStartOfDay());

        for (Long memberId : MEMBERS) {
            long posts =
                    planned.stream()
                            .filter(action -> action.kind() == Kind.POST)
                            .filter(action -> action.memberId().equals(memberId))
                            .count();
            assertThat(posts).isBetween(0L, 5L);
        }
        assertThat(planned.stream().filter(action -> action.kind() == Kind.CHAT)).hasSize(5);
    }

    @Test
    @DisplayName("재시작하면 같은 계획에서 이미 쓴 개수와 지난 시각을 빼고 남은 몫만 돌려준다")
    void replanKeepsOnlyRemainingFutureActions() {
        LocalDate date = LocalDate.of(2026, 12, 20);
        List<Planned> full = AiActivityPlanner.plan(date, MEMBERS, Map.of(), date.atStartOfDay());
        LocalDateTime restart = date.atTime(12, 30);

        List<Planned> afterRestart =
                AiActivityPlanner.plan(date, MEMBERS, Map.of(1L, 5, 2L, 5, 3L, 5, 4L, 5), restart);

        assertThat(afterRestart).allSatisfy(action -> assertThat(action.at()).isAfter(restart));
        assertThat(afterRestart.stream().filter(action -> action.kind() == Kind.POST)).isEmpty();
        assertThat(afterRestart.stream().filter(action -> action.kind() == Kind.CHAT))
                .containsExactlyElementsOf(
                        full.stream()
                                .filter(action -> action.kind() == Kind.CHAT)
                                .filter(action -> action.at().isAfter(restart))
                                .toList());
    }

    @Test
    @DisplayName("활동 시간대 경계: 7시·12시·18시·23시59분은 활동, 6시59분·9시·13시·0시는 쉰다")
    void activeWindowBoundaries() {
        LocalDate date = LocalDate.of(2026, 12, 20);

        assertThat(AiActivityPlanner.isActive(date.atTime(7, 0))).isTrue();
        assertThat(AiActivityPlanner.isActive(date.atTime(12, 0))).isTrue();
        assertThat(AiActivityPlanner.isActive(date.atTime(18, 0))).isTrue();
        assertThat(AiActivityPlanner.isActive(date.atTime(23, 59))).isTrue();
        assertThat(AiActivityPlanner.isActive(date.atTime(6, 59))).isFalse();
        assertThat(AiActivityPlanner.isActive(date.atTime(9, 0))).isFalse();
        assertThat(AiActivityPlanner.isActive(date.atTime(13, 0))).isFalse();
        assertThat(AiActivityPlanner.isActive(date.atTime(0, 0))).isFalse();
    }
}
