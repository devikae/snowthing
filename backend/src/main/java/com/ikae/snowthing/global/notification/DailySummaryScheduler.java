package com.ikae.snowthing.global.notification;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.global.notification.DailySummaryQueryRepository.Split;

import lombok.extern.slf4j.Slf4j;

/**
 * 매일 09:00 KST에 전날(00:00 이상, 다음 날 00:00 미만) 활동 수를 #이벤트 채널로 보낸다. 그 시각에 서버가 내려가 있었으면 건너뛰고 다시 보내지 않는다.
 * 단일 인스턴스 기준이다.
 *
 * <p>created_at은 KST LocalDateTime으로 저장된다(운영 TZ=Asia/Seoul, 접속 serverTimezone=Asia/Seoul).
 */
@Slf4j
@Component
public class DailySummaryScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MM/dd");

    private final DailySummaryQueryRepository queryRepository;
    private final DiscordNotifier notifier;
    private final Clock clock;

    @Autowired
    public DailySummaryScheduler(
            DailySummaryQueryRepository queryRepository, DiscordNotifier notifier) {
        this(queryRepository, notifier, Clock.system(KST));
    }

    DailySummaryScheduler(
            DailySummaryQueryRepository queryRepository, DiscordNotifier notifier, Clock clock) {
        this.queryRepository = queryRepository;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(cron = "${snowthing.discord.daily-summary-cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void sendYesterdaySummary() {
        if (!notifier.eventEnabled()) {
            return;
        }
        try {
            notifier.event(summarize(LocalDate.now(clock.withZone(KST)).minusDays(1)));
        } catch (RuntimeException e) {
            log.warn("일일 요약 집계 실패: {}", e.getClass().getSimpleName(), e);
        }
    }

    String summarize(LocalDate day) {
        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        long signUps = queryRepository.countHumanSignUps(from, to);
        Split posts = queryRepository.countPosts(from, to);
        Split comments = queryRepository.countComments(from, to);
        return "📊 "
                + day.format(DAY)
                + " 요약\n"
                + "신규 가입 "
                + signUps
                + "\n"
                + line("글", posts)
                + "\n"
                + line("댓글", comments);
    }

    private static String line(String label, Split split) {
        return label + " " + split.total() + " (사람 " + split.human() + " / AI " + split.ai() + ")";
    }
}
