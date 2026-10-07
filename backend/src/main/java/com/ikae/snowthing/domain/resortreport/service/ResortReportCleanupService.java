package com.ikae.snowthing.domain.resortreport.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ikae.snowthing.domain.resortreport.repository.ResortReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResortReportCleanupService {

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");

    private final ResortReportRepository resortReportRepository;
    private final Clock clock;

    @Transactional
    @Scheduled(cron = "${snowthing.resort-report.cleanup-cron:0 40 4 1 * *}", zone = "Asia/Seoul")
    public int deletePreviousMonthReports() {
        LocalDate currentMonth = LocalDate.now(clock.withZone(KST_ZONE)).withDayOfMonth(1);
        return resortReportRepository.deleteCreatedBefore(currentMonth.atStartOfDay());
    }
}
