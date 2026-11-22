package com.ikae.snowthing.domain.moderation.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ikae.snowthing.domain.moderation.config.ModerationProperties;
import com.ikae.snowthing.domain.moderation.repository.ContentModerationRepository;
import com.ikae.snowthing.global.notification.DiscordNotifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 오판정 폭주 감지 (IKA-80). 매시 5분에 지난 한 시간(정시 기준) 자동 숨김 수를 세어 기준(기본 10건)을 넘으면 Discord #장애로 알린다. 서버 한 대
 * 기준이다. 그 시각에 서버가 내려가 있었으면 그 시간은 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "${snowthing.moderation.enabled:false} and ${snowthing.moderation.scheduler-enabled:true}")
public class ModerationAlertScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ContentModerationRepository repository;
    private final DiscordNotifier notifier;
    private final ModerationProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${snowthing.moderation.alert-cron:0 5 * * * *}", zone = "Asia/Seoul")
    public void checkLastHour() {
        try {
            String message = lastHourMessage(LocalDateTime.now(clock.withZone(KST)));
            if (message != null) {
                notifier.alert(message);
            }
        } catch (RuntimeException e) {
            log.warn("content moderation hourly check failed: {}", e.getClass().getSimpleName());
        }
    }

    /** 알릴 내용. 기준 이하면 null. */
    String lastHourMessage(LocalDateTime now) {
        LocalDateTime to = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime from = to.minusHours(1);
        long hidden = repository.countVerdicts(from, to).hidden();
        if (hidden <= properties.hourlyAlertThreshold()) {
            return null;
        }
        return "⚠️ 자동 숨김이 "
                + from.getHour()
                + "시대에 "
                + hidden
                + "건입니다. 오판정인지 관리자 검토 대기 화면에서 확인해 주세요.";
    }
}
