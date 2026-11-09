package com.ikae.snowthing.domain.aimember;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiMemberSeasonTest {

    private static final LocalDate OPENING = LocalDate.of(2026, 11, 20);

    @Test
    @DisplayName("첫 개장일 전날까지는 아직 아무도 못 탔다고 알린다")
    void beforeOpening() {
        assertThat(AiMemberActionService.season(LocalDate.of(2026, 11, 9), OPENING))
                .contains("개장 전")
                .contains("못 탔다");
        assertThat(AiMemberActionService.season(LocalDate.of(2026, 11, 19), OPENING))
                .contains("개장 전");
    }

    @Test
    @DisplayName("개장일부터 3주는 개장 초반, 그 뒤는 월 기준이다")
    void afterOpening() {
        assertThat(AiMemberActionService.season(OPENING, OPENING)).contains("개장 초반");
        assertThat(AiMemberActionService.season(LocalDate.of(2026, 12, 10), OPENING))
                .contains("개장 초반");
        assertThat(AiMemberActionService.season(LocalDate.of(2026, 12, 11), OPENING))
                .isEqualTo("시즌 한창");
    }

    @Test
    @DisplayName("개장일 설정이 없거나 지난 시즌 값이면 11월은 탔다고 단정하지 않게 한다")
    void withoutOpeningDate() {
        assertThat(AiMemberActionService.season(LocalDate.of(2027, 11, 5), OPENING))
                .contains("단정하지 않는다");
        assertThat(AiMemberActionService.season(LocalDate.of(2026, 11, 5), null))
                .contains("단정하지 않는다");
    }

    @Test
    @DisplayName("봇 서버 검증 길이 50자를 넘지 않는다")
    void fitsBotLimit() {
        for (int day = 0; day < 365; day++) {
            assertThat(AiMemberActionService.season(OPENING.minusMonths(4).plusDays(day), OPENING))
                    .hasSizeLessThanOrEqualTo(50);
        }
    }
}
