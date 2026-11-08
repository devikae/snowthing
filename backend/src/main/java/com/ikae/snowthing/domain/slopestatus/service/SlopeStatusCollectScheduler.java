package com.ikae.snowthing.domain.slopestatus.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 하루 몇 번만 공식 현황을 읽는다. 기본은 KST 06:30부터 20:30까지 2시간마다(하루 8번)다. 켜 둔 리조트가 없으면 아무것도 부르지 않는다.
 *
 * <p>서버가 여러 대가 되면 대수만큼 호출이 늘어난다. 지금은 한 대라 분산 잠금을 두지 않았다(설계 문서 "결정과 대안").
 */
@Component
@RequiredArgsConstructor
public class SlopeStatusCollectScheduler {

    private final SlopeStatusCollector collector;

    @Scheduled(cron = "${snowthing.slope-status.cron:0 30 6-21/2 * * *}", zone = "Asia/Seoul")
    public void collect() {
        collector.collectEnabled();
    }
}
