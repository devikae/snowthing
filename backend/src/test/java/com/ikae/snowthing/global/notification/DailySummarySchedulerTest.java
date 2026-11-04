package com.ikae.snowthing.global.notification;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ikae.snowthing.global.notification.DailySummaryQueryRepository.Split;

class DailySummarySchedulerTest {

    private final DailySummaryQueryRepository repository = mock(DailySummaryQueryRepository.class);
    private final DiscordNotifier notifier = mock(DiscordNotifier.class);
    // 2026-11-04 08:30 KST. UTC로는 아직 11/03이라 KST 변환이 빠지면 11/02를 센다.
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T23:30:00Z"), ZoneOffset.UTC);
    private final DailySummaryScheduler scheduler =
            new DailySummaryScheduler(repository, notifier, clock);

    @Test
    @DisplayName("전날 KST 00시부터 다음 날 00시 전까지를 집계해 사람과 AI를 나눠 보낸다")
    void sendsYesterdayInKst() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 3, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 4, 0, 0);
        given(notifier.eventEnabled()).willReturn(true);
        given(repository.countHumanSignUps(from, to)).willReturn(3L);
        given(repository.countPosts(from, to)).willReturn(new Split(5, 7));
        given(repository.countComments(from, to)).willReturn(new Split(18, 12));

        scheduler.sendYesterdaySummary();

        verify(notifier)
                .event(
                        "📊 11/03 요약\n"
                                + "신규 가입 3\n"
                                + "글 12 (사람 5 / AI 7)\n"
                                + "댓글 30 (사람 18 / AI 12)");
    }

    @Test
    @DisplayName("이벤트 웹훅이 없으면 집계하지 않는다")
    void skipsWhenEventChannelDisabled() {
        given(notifier.eventEnabled()).willReturn(false);

        scheduler.sendYesterdaySummary();

        verifyNoInteractions(repository);
        verify(notifier, never()).event(anyString());
    }
}
