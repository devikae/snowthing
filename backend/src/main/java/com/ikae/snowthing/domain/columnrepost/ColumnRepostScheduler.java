package com.ikae.snowthing.domain.columnrepost;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 매일 KST 10시, 15시에 칼럼 하나를 올린다. 꺼져 있으면 빈을 만들지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "snowthing.column-repost.enabled", havingValue = "true")
public class ColumnRepostScheduler {

    private final ColumnRepostService service;

    @Scheduled(cron = "${snowthing.column-repost.cron:0 0 10,15 * * *}", zone = "Asia/Seoul")
    public void run() {
        ColumnRepostService.Result result = service.publishNext();
        log.info("Column repost run: result={}", result);
    }
}
