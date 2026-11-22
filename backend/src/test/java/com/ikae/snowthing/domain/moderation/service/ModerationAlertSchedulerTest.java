package com.ikae.snowthing.domain.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.domain.moderation.config.ModerationProperties;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository.VerdictCounts;
import com.ikae.snowthing.global.notification.DiscordNotifier;

/** 시간당 자동 숨김 폭주 알림 (IKA-80). 지난 정시 한 시간 동안 10건을 넘을 때만 알린다. */
class ModerationAlertSchedulerTest {

    private final ContentModerationRepository repository = mock(ContentModerationRepository.class);
    private final ModerationProperties properties =
            new ModerationProperties(true, false, 0, 0, null, 0, null, null, 0, null, null);
    private final ModerationAlertScheduler scheduler =
            new ModerationAlertScheduler(
                    repository, mock(DiscordNotifier.class), properties, Clock.systemUTC());

    private final LocalDateTime now = LocalDateTime.of(2026, 11, 22, 14, 5);
    private final LocalDateTime from = LocalDateTime.of(2026, 11, 22, 13, 0);
    private final LocalDateTime to = LocalDateTime.of(2026, 11, 22, 14, 0);

    @Test
    @DisplayName("10건이면 알리지 않는다")
    void atThresholdNoAlert() {
        given(repository.countVerdicts(from, to)).willReturn(new VerdictCounts(10, 3));
        assertThat(scheduler.lastHourMessage(now)).isNull();
    }

    @Test
    @DisplayName("11건이면 #장애로 보낼 문구를 만든다")
    void overThresholdAlerts() {
        given(repository.countVerdicts(from, to)).willReturn(new VerdictCounts(11, 0));
        assertThat(scheduler.lastHourMessage(now)).contains("13시대에 11건");
    }
}
